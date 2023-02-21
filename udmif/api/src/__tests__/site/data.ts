import { Site } from '../../site/model';
import siteMessage from './siteValidationMessage.json';

export function createSites(count: number): Site[] {
  const sites: Site[] = [];
  let n = 1;

  while (n <= count) {
    const uuid = '00000000-0000-0000-0000-000000000' + pad(n);
    const name = n % 2 == 0 ? `SITE-${n}` : `LOC-${n}`;
    const validation = siteMessage;

    sites.push({
      uuid,
      name,
      validation,
    });
    n++;
  }

  return sites;
}

function pad(num) {
  let s = '000' + num;
  return s.substring(s.length - 3);
}
