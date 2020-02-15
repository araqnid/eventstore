package org.araqnid.eventstore.subscription

import com.google.common.reflect.AbstractInvocationHandler
import com.google.common.reflect.Reflection
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

internal class ListenerSet<L> {
    private val listeners: MutableList<Dispatch<L>> = CopyOnWriteArrayList()

    fun addListener(listener: L, executor: Executor) {
        listeners += Dispatch(listener, executor)
    }

    fun emit(command: (L) -> Unit) {
        listeners.forEach { lp -> lp.executor.execute { command(lp.listener) } }
    }

    fun proxy(clazz: Class<L>): L {
        return Reflection.newProxy(clazz, object : AbstractInvocationHandler() {
            override fun handleInvocation(proxy: Any, method: Method, args: Array<out Any>): Any? {
                emit { method.invoke(it, *args) }
                return null
            }
        })
    }

    private data class Dispatch<out L>(val listener: L, val executor: Executor)
}
