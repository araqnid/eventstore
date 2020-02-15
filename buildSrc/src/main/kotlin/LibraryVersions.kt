import kotlin.reflect.full.memberProperties

object LibraryVersions {
    const val jackson = "2.10.2"
    const val guava = "28.2-jre"
    const val slf4j = "1.7.30"

    fun toMap() =
            LibraryVersions::class.memberProperties
                    .associate { prop -> prop.name to prop.getter.call() as String }
}
