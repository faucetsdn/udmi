import { AppPage } from './app.po';
import { browser, logging } from 'protractor';

describe('workspace-project App', () => {
  let page: AppPage;

  beforeEach(() => {
    page = new AppPage();
  });

  it('should display title', async () => {
    await page.navigateTo();
    expect(await page.getTitleText()).toEqual('UDMS');
  });

  it('should be redirected to signin page', async () => {
    await page.navigateTo('/dashboard');
    expect(await page.getCurrentUrl()).toEqual('login');
  });

  it('should be able to navigate to dashboard when signed in', async () => {
    await page.signIn();
    await page.navigateTo('/dashboard');
    expect(await page.getCurrentUrl()).toEqual('dashboard');
  });

  afterEach(async () => {
    // Assert that there are no errors emitted from the browser
    const logs = await browser.manage().logs().get(logging.Type.BROWSER);
    expect(logs).not.toContain(jasmine.objectContaining({
      level: logging.Level.SEVERE,
    } as logging.Entry));
  });
});
