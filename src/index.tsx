import { NativeModules, NativeEventEmitter } from 'react-native';
import Base from './Base';
import type { GizResult, GizCallback, DeviceDataCallback, DeviceDataRes } from './types';

const eventEmitter = new NativeEventEmitter(NativeModules.RNGizSDKManagerModule)


class GizwitsPadSdkClass extends Base {
  deviceDataCallbacks: DeviceDataCallback[] = []

  constructor() {
    super()
    eventEmitter.addListener('DeviceDataListener', this.deviceDataCallback)
  }

  deviceDataCallback = (data: DeviceDataRes) => {
    this.deviceDataCallbacks.map((item) => {
      item(data)
    })
  }

  removeDeviceDataListener(callback: DeviceDataCallback) {
    this.deleteCallBack(this.deviceDataCallbacks, callback)
  }

  addDeviceDataListener(callback: DeviceDataCallback) {
    this.deviceDataCallbacks.push(callback)
  }
  public async setLedStatus(status: boolean): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.setLedStatus(
        { status },
        callback
      )
    })
  }
  public async setBreathingLight(index: number, isBreath: boolean, effect: number): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.setBreathingLight(
        { index, isBreath, effect },
        callback
      )
    })
  }
  public async setRelay(index: number, status: boolean): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.setRelay(
        { index, status },
        callback
      )
    })
  }

  public async getRelay(index: number): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.getRelay(
        { index },
        callback
      )
    })
  }
  public async set485Port(index: number, status: boolean): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.set485Port(
        { index, status },
        callback
      )
    })
  }
  
  public async get485Port(index: number): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.set485Port(
        { index },
        callback
      )
    })
  }
  public async send485PortMessage(data: string, isHex: boolean): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.send485PortMessage(
        { data,isHex },
        callback
      )
    })
  }
  public async factoryReset(): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.factoryReset(
        {  },
        callback
      )
    })
  }
}

const GizwitsPadSdk = new GizwitsPadSdkClass();
export default GizwitsPadSdk;

export type {DeviceDataRes}