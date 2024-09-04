

export interface GizResult<T, E> {
  success: boolean
  message: string
  data?: T
  error?: E
}
export type GizCallback<T, E> = (
  error: GizResult<T, E>,
  data: GizResult<T, E>
) => void
export interface ServerInfoStruct {
  openAPIInfo: string
}
export interface ProductInfoStruct {
  productKey: string
  productSecret: string
}
export interface GizConfigStruct {
  appID: string
  appSecret: string
  productInfos: ProductInfoStruct[]
  serverInfo?: ServerInfoStruct
}


export interface DeviceDataRes {
  data: string
}
export type DeviceDataCallback = (data: DeviceDataRes) => void
