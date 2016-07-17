import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.psi.PsiElement
import com.jetbrains.cidr.cpp.CPPToolchains
import com.jetbrains.cidr.cpp.cmake.psi.CMakeCommandName
import com.jetbrains.cidr.cpp.cmake.psi.CMakeElement
import com.jetbrains.cidr.cpp.cmake.psi.CMakeLiteral
import org.asciidoc.intellij.AsciiDoc
import org.jetbrains.rpc.LOG
import java.io.IOException
import java.util.ArrayList

class CMakeDocProvider : AbstractDocumentationProvider() {

  private var moduleList = ArrayList<String>()

  private var propertyList = ArrayList<String>()

  private var variableList = ArrayList<String>()

  private val asciiDoc by lazy { AsciiDoc(createTempDir()) }

  fun runCMake(vararg args: String): Process {
    val cmake = CPPToolchains.getInstance().cMake!!
    LOG.info("CDP - Executing CMake: ${cmake.executablePath} ${args.joinToString(" ")}")
    return ProcessBuilder(cmake.executablePath, *args).start()
  }

  fun <T> withLinesFromCMake(vararg args: String, block: (Sequence<String>) -> T): T? {
    try {
      return runCMake(*args).inputStream.bufferedReader().useLines { block(it) }
    } catch (e: IOException) {
      e.printStackTrace()
      Notifications.Bus.notify(Notification("ApplicationName", "CMake Docs", "Unable to run CMake to get docs", NotificationType.ERROR))
    }
    return null
  }

  fun getLinesFromCMake(vararg args: String, block: (Sequence<String>) -> Sequence<String> = { it }): List<String> {
    return withLinesFromCMake(*args) { block(it).toList() } ?: emptyList()
  }

  fun docForCommand(cmd: String): String? = withLinesFromCMake("--help-command", cmd) { it.joinToString("\n") }

  fun docForLiteral(literal: String): String? {
    val lit = literal.removeSurrounding("\${", "}") /* remove expansion syntax if present */
    if (moduleList.isEmpty()) {
      moduleList.addAll(getLinesFromCMake("--help-module-list"))
    }
    if (lit in moduleList) {
      return withLinesFromCMake("--help-module", lit) { it.joinToString("\n") }
    }
    if (propertyList.isEmpty()) {
      propertyList.addAll(getLinesFromCMake("--help-property-list"))
    }
    if (lit in propertyList) {
      return withLinesFromCMake("--help-property", lit) { it.joinToString("\n") }
    }
    if (variableList.isEmpty()) {
      variableList.addAll(getLinesFromCMake("--help-variable-list"))
    }
    if (lit in variableList) {
      return withLinesFromCMake("--help-variable", lit) { it.joinToString("\n") }
    }
    return null
  }

  override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
    return if (element == null || element !is CMakeElement) {
      null
    } else {
      when (element) {
        is CMakeCommandName -> docForCommand(element.name)
        is CMakeLiteral -> docForLiteral(element.text)
        else -> null
      }.let {
        if (it == null) null else asciiDoc.render(it)
      }
    }
  }
}
