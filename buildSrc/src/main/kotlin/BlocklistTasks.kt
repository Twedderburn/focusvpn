import com.focusvpn.blocklist.Blocklist
import com.focusvpn.blocklist.Category
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** blocklists/<name>-domains.txt: the list as it is now. */
internal fun listFile(dir: File, name: String) = File(dir, "$name-domains.txt")

/** blocklists/released/<name>-domains.txt.gz: the list as it was in the last release. */
internal fun releasedFile(dir: File, name: String) = File(dir, "released/$name-domains.txt.gz")

/**
 * Turns blocklists/<name>-domains.txt into the app's blocklist.bin asset, and fails the build if any name from the
 * last release's lists (blocklists/released) is no longer blocked. Lists may only grow (PRD §5).
 */
abstract class BuildBlocklistTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val listDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = listDir.get().asFile
        val lists = Category.LISTS.mapNotNull { (name, bit) ->
            val file = listFile(dir, name)
            when {
                file.exists() -> Triple(name, bit, file)
                releasedFile(dir, name).exists() ->
                    throw GradleException("Grow-only check failed: ${file.name} is missing but the last release had it.")
                else -> null
            }
        }
        val entries = lists.asSequence().flatMap { (_, bit, file) ->
            file.useLines { Blocklist.parseList(it).toList() }.asSequence().map { it to bit }
        }
        val table = Blocklist.buildTable(entries)

        for ((name, bit, _) in lists) {
            val released = releasedFile(dir, name)
            if (!released.exists()) continue
            val dropped = GZIPInputStream(released.inputStream()).bufferedReader().useLines { lines ->
                Blocklist.noLongerBlocked(Blocklist.parseList(lines), table, bit)
            }
            if (dropped.isNotEmpty()) {
                throw GradleException(
                    "Grow-only check failed: ${dropped.size} sites from the last release's $name list are no longer " +
                        "blocked, e.g. ${dropped.take(10).joinToString()}. Lists may only grow (PRD §5).",
                )
            }
        }

        val out = outputDir.get().asFile
        out.mkdirs()
        val bin = File(out, Blocklist.FILE_NAME)
        bin.writeBytes(Blocklist.encode(table))
        logger.lifecycle("${Blocklist.FILE_NAME}: ${table.size} entries, ${bin.length() / 1024} KiB")
    }
}

/**
 * Run when cutting a release: records the current lists as the release baseline that later builds must keep
 * blocking. Never run it to make a failing grow-only check pass.
 */
abstract class SnapshotReleasedBlocklistsTask : DefaultTask() {
    @get:InputDirectory
    abstract val listDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = listDir.get().asFile
        for (name in Category.LISTS.keys) {
            val file = listFile(dir, name)
            if (!file.exists()) continue
            val out = releasedFile(dir, name)
            out.parentFile.mkdirs()
            val domains = file.useLines { Blocklist.parseList(it).toSortedSet() }
            GZIPOutputStream(out.outputStream()).bufferedWriter().use { w -> domains.forEach { w.write(it); w.write("\n") } }
            logger.lifecycle("${out.name}: ${domains.size} domains")
        }
    }
}
