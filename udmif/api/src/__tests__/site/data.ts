import { Site } from '../../site/model';
import siteMessage from './siteValidationMessage.json';

export function createSites(count: number): Site[] {
  const sites: Site[] = [];
  let n = 1;

  while (n <= count) {
    const id = '00000000-0000-0000-0000-000000000' + n;
    const name = n % 2 == 0 ? `SITE-${n}` : `LOC-${n}`;
    const validation = siteMessage;

    sites.push({
      id,
      name,
      validation,
    });
    n++;
  }

  return sites;
}
