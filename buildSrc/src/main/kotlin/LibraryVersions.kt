import kotlin.reflect.full.memberProperties

object LibraryVersions {
    const val jackson = "2.10.2"
    const val guava = "28.2-jre"

    fun toMap() =
            LibraryVersions::class.memberProperties
                    .associate { prop -> prop.name to prop.getter.call() as String }
}
