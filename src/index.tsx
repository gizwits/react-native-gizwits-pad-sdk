import { NativeModules } from 'react-native';
import Base from './Base';
import type { GizResult, GizCallback } from './types';

class GizwitsPadSdkClass extends Base {
  async getHomeSensorData(): Promise<GizResult<any, any>> {
    return this.callbackWapper((callback: GizCallback<any, any>) => {
      NativeModules.GizwitsPadSdk.getHomeSensorData(
        {  },
        callback
      )
    })
  }
}


const GizwitsPadSdk = new GizwitsPadSdkClass();
export {GizwitsPadSdk}
