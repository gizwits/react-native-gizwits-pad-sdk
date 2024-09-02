import type { GizCallback, GizResult } from './types'

class Base {
  callbackWapper(
    func: (callback: GizCallback<any, any>) => void
  ): Promise<GizResult<any, any>> {
    const promise = new Promise((res, _) => {
      func((error, data) => {
        if (error) {
          res(error)
        } else {
          res(data)
        }
      })
    })

    return promise as Promise<GizResult<any, any>>
  }

  deleteCallBack = (callbacks: any[], callback: any) => {
    const index = callbacks.findIndex((item) => item === callback)
    if (index !== -1) {
      callbacks.splice(index, 1)
    }
  }
}

export default Base
