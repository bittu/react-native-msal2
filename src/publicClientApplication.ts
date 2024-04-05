import { Platform } from 'react-native'

import type {
  MSALConfiguration,
  MSALInteractiveParams,
  MSALSilentParams,
  MSALAccount,
  MSALSignoutParams,
  IPublicClientApplication,
  MSALResult,
} from './types'

import { NativeModules } from 'react-native'

type RNMSALNativeModule = {
  createPublicClientApplication(config: MSALConfiguration): Promise<void>
  acquireToken(params: MSALInteractiveParams): Promise<MSALResult | undefined>
  acquireTokenSilent(params: MSALSilentParams): Promise<MSALResult | undefined>
  getAccounts(): Promise<MSALAccount[]>
  getAccount(accountIdentifier: string): Promise<MSALAccount | undefined>
  removeAccount(account: MSALAccount): Promise<boolean>
  signout(params: MSALSignoutParams): Promise<boolean>
}

const RNMSAL: RNMSALNativeModule = NativeModules.RNMSAL

export default RNMSAL

export class PublicClientApplication implements IPublicClientApplication {
  private isInitialized: boolean = false

  constructor(private readonly config: MSALConfiguration) {}

  public async init() {
    if (!this.isInitialized) {
      await RNMSAL.createPublicClientApplication(this.config)
      this.isInitialized = true
    }
    return this
  }

  public async acquireToken(params: MSALInteractiveParams) {
    this.validateIsInitialized()
    return await RNMSAL.acquireToken(params)
  }

  public async acquireTokenSilent(params: MSALSilentParams) {
    this.validateIsInitialized()
    return await RNMSAL.acquireTokenSilent(params)
  }

  public async getAccounts() {
    this.validateIsInitialized()
    return await RNMSAL.getAccounts()
  }

  public async getAccount(accountIdentifier: string) {
    this.validateIsInitialized()
    return await RNMSAL.getAccount(accountIdentifier)
  }

  public async removeAccount(account: MSALAccount) {
    this.validateIsInitialized()
    return await RNMSAL.removeAccount(account)
  }

  public async signOut(params: MSALSignoutParams) {
    this.validateIsInitialized()
    return await Platform.select({
      ios: async () => await RNMSAL.signout(params),
      default: async () => await RNMSAL.removeAccount(params.account),
    })()
  }

  private validateIsInitialized() {
    if (!this.isInitialized) {
      throw new Error(
        'PublicClientApplication is not initialized. You must call the `init` method before any other method.',
      )
    }
  }
}
