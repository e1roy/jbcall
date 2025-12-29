package io.github.e1roy.jbcall.util

import com.intellij.lang.jvm.types.JvmType
import com.intellij.psi.PsiType

/**
 * 类型工具类，用于处理 PsiType 的转换
 */
object TypeUtils {
    
    /**
     * 从 PsiType 获取全限定类名
     */
    fun getTypeQualifiedName(psiType: PsiType?): String {
        return psiType?.canonicalText ?: "void"
    }
    
    /**
     * 从 JvmType 获取全限定类名（兼容处理，接收任意类型并尝试提取可用信息）
     */
    fun getTypeQualifiedName(jvmType: JvmType?): String {
        if (jvmType == null) return "void"

        return when (jvmType) {
            is PsiType -> jvmType.canonicalText
            else -> jvmType.toString()
        }
    }
}