import { browser, by, element, until } from 'protractor';

export class AppPage {
  async navigateTo(page?: string): Promise<unknown> {
    return browser.get(browser.baseUrl + (page ? page : ''));
  }

  async getTitleText(): Promise<string> {
    return element(by.css('app-root mat-toolbar .title')).getText();
  }

  async getCurrentUrl(): Promise<string> {
    return (await browser.getCurrentUrl()).replace(browser.baseUrl, '');
  }

  async signIn(): Promise<void> {
    await this.navigateTo('/login');
    await element(by.css('app-root app-login button')).click();
    const currentWindow = await browser.getWindowHandle();
    const windows = await browser.getAllWindowHandles();
    const oAuthWindow = windows.filter((window) => {
      return window !== currentWindow;
    })[0];
    await browser.waitForAngularEnabled(false);
    await browser.switchTo().window(oAuthWindow);
    await browser.wait(until.elementLocated(by.css('#content button')), 10000);
    await element(by.css('#content button')).click();
    await element(by.css('#autogen-button')).click();
    await element(by.css('#sign-in')).click();
    await browser.switchTo().window(currentWindow);
    await browser.waitForAngularEnabled(true);
    await browser.wait(until.elementLocated(by.css('app-dashboard')), 10000);
  }

}
