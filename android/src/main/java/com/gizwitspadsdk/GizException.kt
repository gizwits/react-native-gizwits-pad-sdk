package com.gizwitspadsdk

/**
 * SDK通用的异常类
 * @param errorMessage 错误消息
 * @param cause 触发此异常的原因
 */

public open class GizException(
    errorMessage: String,
    public val errorCode: Int,
    cause: Throwable? = null
) : Exception(errorMessage, cause) {
    public companion object {

        private const val serialVersionUID = -18827L

        public val SDK_NOT_REGISTERED: GizException = GizException("SDK not registered", -1)

        public val SDK_IS_REGISTERED: GizException = GizException("SDK is registered", -2)

        public val GIZ_SDK_PARAM_INVALID: GizException = GizException("Params error", 5001)

        public val GIZ_SITE_PRODUCTKEY_INVALID: GizException = GizException("Product Not Found", 10003)

        public val GIZ_SDK_DEVICE_NOT_BOUND: GizException = GizException("Device Not Bound", 9017)
        public val GIZ_SDK_OTHERWISE: GizException = GizException("other error", 8100)
        public val GIZ_OPENAPI_DEVICE_NOT_FOUND: GizException = GizException("GIZ_OPENAPI_DEVICE_NOT_FOUND", 9014)


    }

}