package dev.toliner.spector.storage

import dev.toliner.spector.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * SQLite-based type indexer for storing and querying class and member information.
 */
class TypeIndexer(private val dbPath: String) : AutoCloseable {

    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
    private val json = Json {
        prettyPrint = false
        classDiscriminator = "@class"  // Avoid conflict with 'type' property in FieldInfo
    }

    init {
        // Enable SQLite performance optimizations
        connection.createStatement().use { stmt ->
            // Use WAL mode for better concurrent access and performance
            stmt.executeUpdate("PRAGMA journal_mode=WAL")
            // Normal synchronous mode is faster and safe enough for most cases
            stmt.executeUpdate("PRAGMA synchronous=NORMAL")
            // Increase cache size (in pages, negative number = KB)
            stmt.executeUpdate("PRAGMA cache_size=-64000") // 64MB cache
        }
        createTables()
    }

    private fun createTables() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS types (
                    fqcn TEXT PRIMARY KEY,
                    package_name TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    modifiers TEXT NOT NULL,
                    super_class TEXT,
                    interfaces TEXT,
                    type_parameters TEXT,
                    annotations TEXT,
                    kotlin_info TEXT,
                    data TEXT NOT NULL
                )
                """.trimIndent()
            )

            stmt.executeUpdate(
                """
                CREATE INDEX IF NOT EXISTS idx_package_name ON types(package_name)
                """.trimIndent()
            )

            stmt.executeUpdate(
                """
                CREATE INDEX IF NOT EXISTS idx_super_class ON types(super_class)
                """.trimIndent()
            )

            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_fqcn TEXT NOT NULL,
                    name TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    jvm_desc TEXT,
                    visibility TEXT NOT NULL,
                    static INTEGER NOT NULL,
                    data TEXT NOT NULL,
                    UNIQUE(owner_fqcn, name, jvm_desc)
                )
                """.trimIndent()
            )

            stmt.executeUpdate(
                """
                CREATE INDEX IF NOT EXISTS idx_owner_fqcn ON members(owner_fqcn)
                """.trimIndent()
            )
        }
    }

    fun indexClass(classInfo: ClassInfo) {
        val modifiersJson = json.encodeToString(classInfo.modifiers.toList())
        val interfacesJson = json.encodeToString(classInfo.interfaces)
        val typeParametersJson = json.encodeToString(classInfo.typeParameters)
        val annotationsJson = json.encodeToString(classInfo.annotations)
        val kotlinInfoJson = classInfo.kotlin?.let { json.encodeToString(it) }
        val dataJson = json.encodeToString(classInfo)

        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO types
            (fqcn, package_name, kind, modifiers, super_class, interfaces, type_parameters, annotations, kotlin_info, data)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, classInfo.fqcn)
            stmt.setString(2, classInfo.packageName)
            stmt.setString(3, classInfo.kind.name)
            stmt.setString(4, modifiersJson)
            stmt.setString(5, classInfo.superClass)
            stmt.setString(6, interfacesJson)
            stmt.setString(7, typeParametersJson)
            stmt.setString(8, annotationsJson)
            stmt.setString(9, kotlinInfoJson)
            stmt.setString(10, dataJson)
            stmt.executeUpdate()
        }
    }

    fun indexMember(member: MemberInfo) {
        val kind = when (member) {
            is FieldInfo -> "FIELD"
            is MethodInfo -> if (member.isConstructor) "CONSTRUCTOR" else "METHOD"
            is PropertyInfo -> "PROPERTY"
        }

        val jvmDesc = when (member) {
            is FieldInfo -> member.jvmDesc
            is MethodInfo -> member.jvmDesc
            is PropertyInfo -> null
        }

        val dataJson = json.encodeToString<MemberInfo>(member)

        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO members
            (owner_fqcn, name, kind, jvm_desc, visibility, static, data)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, member.ownerFqcn)
            stmt.setString(2, member.name)
            stmt.setString(3, kind)
            stmt.setString(4, jvmDesc)
            stmt.setString(5, member.visibility.name)
            stmt.setInt(6, if (member.static) 1 else 0)
            stmt.setString(7, dataJson)
            stmt.executeUpdate()
        }
    }

    fun findClassesByPackage(
        packageName: String,
        recursive: Boolean = true,
        kinds: Set<ClassKind>? = null,
        publicOnly: Boolean = true
    ): List<ClassInfo> {
        val query = buildString {
            append("SELECT data FROM types WHERE ")
            if (recursive) {
                append("(package_name = ? OR package_name LIKE ?)")
            } else {
                append("package_name = ?")
            }
            if (kinds != null && kinds.isNotEmpty()) {
                append(" AND kind IN (${kinds.joinToString(",") { "'$it'" }})")
            }
            if (publicOnly) {
                append(" AND modifiers LIKE '%PUBLIC%'")
            }
            append(" ORDER BY fqcn")
        }

        return connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, packageName)
            if (recursive) {
                stmt.setString(2, "$packageName.%")
            }
            val rs = stmt.executeQuery()
            buildList {
                while (rs.next()) {
                    val dataJson = rs.getString("data")
                    add(json.decodeFromString<ClassInfo>(dataJson))
                }
            }
        }
    }

    fun findClassByFqcn(fqcn: String): ClassInfo? {
        return connection.prepareStatement("SELECT data FROM types WHERE fqcn = ?").use { stmt ->
            stmt.setString(1, fqcn)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                json.decodeFromString<ClassInfo>(rs.getString("data"))
            } else {
                null
            }
        }
    }

    /**
     * Find direct subpackages of the given package.
     *
     * @param packageName The parent package name (e.g., "kotlin")
     * @return List of direct subpackage names (e.g., ["kotlin.collections", "kotlin.text"])
     */
    fun findSubpackages(packageName: String): List<String> {
        val query = """
            SELECT DISTINCT package_name
            FROM types
            WHERE package_name LIKE ?
            ORDER BY package_name
        """.trimIndent()

        return connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, "$packageName.%")
            val rs = stmt.executeQuery()

            val allSubpackages = buildList {
                while (rs.next()) {
                    add(rs.getString("package_name"))
                }
            }

            // Filter to get only direct children (one level deeper)
            val parentPrefix = "$packageName."
            val parentDepth = packageName.count { it == '.' }

            allSubpackages
                .filter { it.startsWith(parentPrefix) }
                .map { subpackage ->
                    // Extract the direct child package name
                    val remaining = subpackage.removePrefix(parentPrefix)
                    val firstDotIndex = remaining.indexOf('.')
                    if (firstDotIndex == -1) {
                        // This is a direct child
                        subpackage
                    } else {
                        // This is a nested package, get only the direct child part
                        parentPrefix + remaining.substring(0, firstDotIndex)
                    }
                }
                .distinct()
                .sorted()
        }
    }

    fun findMembersByOwner(
        ownerFqcn: String,
        kinds: Set<MemberKind>? = null,
        visibilities: Set<Visibility>? = null,
        includeSynthetic: Boolean = false
    ): List<MemberInfo> {
        val query = buildString {
            append("SELECT data FROM members WHERE owner_fqcn = ?")
            if (kinds != null && kinds.isNotEmpty()) {
                append(" AND kind IN (${kinds.joinToString(",") { "'$it'" }})")
            }
            if (visibilities != null && visibilities.isNotEmpty()) {
                append(" AND visibility IN (${visibilities.joinToString(",") { "'$it'" }})")
            }
            append(" ORDER BY name")
        }

        return connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, ownerFqcn)
            val rs = stmt.executeQuery()
            buildList {
                while (rs.next()) {
                    val dataJson = rs.getString("data")
                    val member = json.decodeFromString<MemberInfo>(dataJson)

                    // Filter synthetic if needed
                    val isSynthetic = when (member) {
                        is FieldInfo -> member.isSynthetic
                        is MethodInfo -> member.isSynthetic
                        is PropertyInfo -> false
                    }

                    if (includeSynthetic || !isSynthetic) {
                        add(member)
                    }
                }
            }
        }
    }

    fun findMemberBySignature(ownerFqcn: String, name: String, jvmDesc: String): MemberInfo? {
        return connection.prepareStatement(
            "SELECT data FROM members WHERE owner_fqcn = ? AND name = ? AND jvm_desc = ?"
        ).use { stmt ->
            stmt.setString(1, ownerFqcn)
            stmt.setString(2, name)
            stmt.setString(3, jvmDesc)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                json.decodeFromString<MemberInfo>(rs.getString("data"))
            } else {
                null
            }
        }
    }

    /**
     * Find all direct subclasses of the given class.
     *
     * @param fqcn The fully qualified class name
     * @return List of FQCNs of direct subclasses
     */
    fun findSubclasses(fqcn: String): List<String> {
        return connection.prepareStatement(
            "SELECT fqcn FROM types WHERE super_class = ? ORDER BY fqcn"
        ).use { stmt ->
            stmt.setString(1, fqcn)
            val rs = stmt.executeQuery()
            buildList {
                while (rs.next()) {
                    add(rs.getString("fqcn"))
                }
            }
        }
    }

    /**
     * Find all classes that implement the given interface.
     *
     * @param interfaceFqcn The fully qualified interface name
     * @return List of ClassInfo objects that implement the interface
     */
    fun findImplementations(interfaceFqcn: String): List<ClassInfo> {
        return connection.prepareStatement(
            "SELECT data FROM types WHERE interfaces LIKE ? ORDER BY fqcn"
        ).use { stmt ->
            // Use LIKE with JSON array pattern matching
            stmt.setString(1, "%\"$interfaceFqcn\"%")
            val rs = stmt.executeQuery()
            buildList {
                while (rs.next()) {
                    val classInfo = json.decodeFromString<ClassInfo>(rs.getString("data"))
                    // Verify the interface is actually in the list (LIKE can have false positives)
                    if (classInfo.interfaces.contains(interfaceFqcn)) {
                        add(classInfo)
                    }
                }
            }
        }
    }

    /**
     * Find the superclass chain from the given class up to java.lang.Object.
     *
     * @param fqcn The fully qualified class name
     * @return List of FQCNs representing the inheritance chain (excluding the input class itself)
     */
    fun findSuperclassChain(fqcn: String): List<String> {
        val chain = mutableListOf<String>()
        var currentFqcn = fqcn

        while (true) {
            val classInfo = findClassByFqcn(currentFqcn) ?: break
            val superClass = classInfo.superClass ?: break
            chain.add(superClass)
            currentFqcn = superClass

            // Safety check to prevent infinite loops
            if (chain.size > 100) break
        }

        return chain
    }

    /**
     * Find all interfaces implemented by the given class, including inherited ones.
     *
     * @param fqcn The fully qualified class name
     * @return Set of interface FQCNs (transitive closure)
     */
    fun findAllInterfaces(fqcn: String): Set<String> {
        val allInterfaces = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(fqcn)

        while (queue.isNotEmpty()) {
            val currentFqcn = queue.removeFirst()
            if (currentFqcn in visited) continue
            visited.add(currentFqcn)

            val classInfo = findClassByFqcn(currentFqcn) ?: continue

            // Add direct interfaces
            allInterfaces.addAll(classInfo.interfaces)

            // Queue interfaces for recursive processing (interfaces can extend other interfaces)
            queue.addAll(classInfo.interfaces.filter { it !in visited })

            // Queue superclass
            classInfo.superClass?.let { if (it !in visited) queue.add(it) }

            // Safety check
            if (visited.size > 1000) break
        }

        return allInterfaces
    }

    /**
     * Find all inherited members from superclasses and interfaces.
     *
     * @param fqcn The fully qualified class name
     * @param kinds Filter by member kinds (null = all kinds)
     * @param visibilities Filter by visibility (null = all visibilities)
     * @param includeSynthetic Whether to include synthetic members
     * @return List of inherited members (excludes members declared in the class itself)
     */
    fun findInheritedMembers(
        fqcn: String,
        kinds: Set<MemberKind>? = null,
        visibilities: Set<Visibility>? = null,
        includeSynthetic: Boolean = false
    ): List<MemberInfo> {
        val inheritedMembers = mutableListOf<MemberInfo>()
        val seenSignatures = mutableSetOf<Pair<String, String?>>() // (name, jvmDesc)

        // First, get all members of the target class itself to exclude overridden methods
        val ownMembers = findMembersByOwner(
            ownerFqcn = fqcn,
            kinds = null, // Get all kinds to check for overrides
            visibilities = null, // Get all visibilities
            includeSynthetic = includeSynthetic
        )

        // Add own member signatures to seen set so they won't be included from parent classes
        for (member in ownMembers) {
            val jvmDesc = when (member) {
                is FieldInfo -> member.jvmDesc
                is MethodInfo -> member.jvmDesc
                is PropertyInfo -> null
            }
            seenSignatures.add(member.name to jvmDesc)
        }

        // Get the superclass chain
        val superclasses = findSuperclassChain(fqcn)

        // Get all interfaces
        val interfaces = findAllInterfaces(fqcn).toList()

        // Combine superclasses and interfaces, superclasses first (for proper overriding)
        val hierarchy = superclasses + interfaces

        for (ancestorFqcn in hierarchy) {
            val members = findMembersByOwner(
                ownerFqcn = ancestorFqcn,
                kinds = kinds,
                visibilities = visibilities,
                includeSynthetic = includeSynthetic
            )

            for (member in members) {
                // Only include public and protected members (private are not inherited)
                if (member.visibility != Visibility.PUBLIC && member.visibility != Visibility.PROTECTED) {
                    continue
                }

                // Skip constructors (they are not inherited)
                if (member is MethodInfo && member.isConstructor) {
                    continue
                }

                // Create signature for deduplication
                val jvmDesc = when (member) {
                    is FieldInfo -> member.jvmDesc
                    is MethodInfo -> member.jvmDesc
                    is PropertyInfo -> null
                }
                val signature = member.name to jvmDesc

                // Add if not overridden (first occurrence wins, which is the closest in the hierarchy)
                if (signature !in seenSignatures) {
                    seenSignatures.add(signature)
                    inheritedMembers.add(member)
                }
            }
        }

        return inheritedMembers
    }

    /**
     * Begin a transaction for batch operations.
     * Must be followed by either commitTransaction() or rollbackTransaction().
     */
    fun beginTransaction() {
        connection.autoCommit = false
    }

    /**
     * Commit the current transaction.
     */
    fun commitTransaction() {
        connection.commit()
        connection.autoCommit = true
    }

    /**
     * Rollback the current transaction.
     */
    fun rollbackTransaction() {
        connection.rollback()
        connection.autoCommit = true
    }

    fun clear() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate("DELETE FROM types")
            stmt.executeUpdate("DELETE FROM members")
        }
    }

    override fun close() {
        connection.close()
    }
}
