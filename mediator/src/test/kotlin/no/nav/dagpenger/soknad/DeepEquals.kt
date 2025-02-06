package no.nav.dagpenger.soknad

import org.junit.jupiter.api.Assertions
import java.math.BigDecimal
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

object DeepEquals {
    fun assertDeepEquals(
        one: Any?,
        other: Any?,
    ) {
        ModelDeepEquals().assertDeepEquals(one, other, "ROOT")
    }
}

private class ModelDeepEquals {
    val checkLog = mutableListOf<Pair<Any, Any>>()

    fun assertDeepEquals(
        one: Any?,
        other: Any?,
        fieldName: String,
    ) {
        if (one is Lazy<*> && other is Lazy<*>) return
        if (one == null && other == null) return
        Assertions.assertFalse(one == null || other == null, "For field $fieldName: $one or $other is null")
        requireNotNull(one)
        if (one::class.qualifiedName == null) return // System class
        checkLog.forEach {
            if (it.first == one && it.second == other) return // Already checked it
        }
        checkLog.add(one to other!!)

        if (one is Collection<*> && other is Collection<*>) {
            assertCollectionEquals(one, other, fieldName)
        } else if (one is Map<*, *> && other is Map<*, *>) {
            assertMapEquals(one, other, fieldName)
        } else {
            assertObjectEquals(one, other, fieldName)
        }
    }

    private fun assertObjectEquals(
        one: Any,
        other: Any,
        fieldName: String,
    ) {
        Assertions.assertEquals(
            one::class,
            other::class,
            "Mismatching classes: ${one.javaClass.simpleName} vs ${other.javaClass.simpleName} for field: $fieldName",
        )
        if (one is Enum<*> && other is Enum<*>) {
            Assertions.assertEquals(one, other, "Failure on enum: $fieldName")
        }
        if (one::class.qualifiedName!!.startsWith("no.nav.dagpenger.")) {
            assertDagpengerObjectEquals(one, other)
        } else if (one is BigDecimal && other is BigDecimal) {
            Assertions.assertEquals(one.toLong(), other.toLong(), "Failure for BigDecimal: $fieldName")
        } else {
            Assertions.assertEquals(one, other, "Failure for field: $fieldName of type: ${one.javaClass.simpleName}")
        }
    }

    private fun assertDagpengerObjectEquals(
        one: Any,
        other: Any,
    ) {
        one::class.memberProperties
            .filterNot { it.isLateinit }
            .map { it.apply { isAccessible = true } }
            .forEach { prop ->
                assertDeepEquals(prop.call(one), prop.call(other), prop.name)
            }
    }

    private fun assertMapEquals(
        one: Map<*, *>,
        other: Map<*, *>,
        fieldName: String,
    ) {
        Assertions.assertEquals(one.size, other.size)
        one.keys.forEach {
            assertDeepEquals(one[it], other[it], fieldName)
        }
    }

    private fun assertCollectionEquals(
        one: Collection<*>,
        other: Collection<*>,
        fieldName: String,
    ) {
        Assertions.assertEquals(one.size, other.size, "Failure for size of field: $fieldName")
        (one.toTypedArray() to other.toTypedArray()).forEach { i1, i2 ->
            this.assertDeepEquals(i1, i2, fieldName)
        }
    }

    private fun Pair<Array<*>, Array<*>>.forEach(block: (Any?, Any?) -> Unit) {
        first.forEachIndexed { i, any -> block(any, second[i]) }
    }
}
