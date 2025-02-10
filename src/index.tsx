import { NativeModules, NativeEventEmitter } from 'react-native';
import Base from './Base';
import type { GizResult, GizCallback, DeviceDataCallback, DeviceDataRes } from './types';

const eventEmitter = new NativeEventEmitter(NativeModules.RNGizSDKManagerModule)

const RNGizwitsPadSdkJSI: {
  sendData(data:string): boolean;
  // @ts-ignore
} = global as any;

export function isLoaded() {
  return typeof RNGizwitsPadSdkJSI.sendData === 'function';
}
if (!isLoaded()) {
  const result = NativeModules.GizwitsPadSdk?.install();
  if (!result && !isLoaded()) { throw new Error('JSI bindings were not installed for: RNGizwitsRnSdk Module'); }

  if (!isLoaded()) {
    throw new Error('JSI bindings were not installed for: RNGizwitsRnSdk Module');
  }
}

class GizwitsPadSdkClass extends Base {
  deviceDataCallbacks?: DeviceDataCallback

  constructor() {
    super()
    // eventEmitter.removeAllListeners('DeviceDataListener')
    eventEmitter.addListener('DeviceDataListener', this.deviceDataCallback)
  }

  deviceDataCallback = (data: DeviceDataRes) => {
    // console.log('deviceDataCallback', data)
    this.deviceDataCallbacks && this.deviceDataCallbacks(data)
    // this.deviceDataCallbacks.map((item) => {
    //   item(data)
    // })
  }

  removeDeviceDataListener(_: DeviceDataCallback) {
    // this.deleteCallBack(this.deviceDataCallbacks, callback)
    this.deviceDataCallbacks = undefined;
  }


  addDeviceDataListener(callback: DeviceDataCallback) {
    this.deviceDataCallbacks= callback
  }
  public async setLedStatus(status: boolean): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.setLedStatus(
        { status },
        callback
      )
    })
  }
  public async startOtaUpdate(filePath: string, softVersion: string): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.startOtaUpdate(
        { filePath, softVersion },
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

  public async initSdk(): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.initSdk(
        {  },
        callback
      )
    })
  }
  public async updateModbusData(data: {index: number, text: string}[]): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.updateModbusData(
        { data },
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

export {RNGizwitsPadSdkJSI}
export type {DeviceDataRes}