import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { startCase } from 'lodash-es';
import { Breadcrumb } from './breadcrumb';

@Component({
  selector: 'app-breadcrumb',
  templateUrl: './breadcrumb.component.html',
  styleUrls: ['./breadcrumb.component.scss'],
})
export class BreadcrumbComponent implements OnInit {
  items: Breadcrumb[] = [];

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this._getSlugs();
  }

  private _getSlugs(): void {
    const slugs: string[] = [];
    let parent: ActivatedRoute | null = this.route.parent;

    while (parent && parent.snapshot.url[0]) {
      slugs.unshift(parent.snapshot.url[0].path);

      parent = parent.parent;
    }

    this.items = slugs.reduce<Breadcrumb[]>((prevItems, slug, i) => {
      const prevItem: Breadcrumb = prevItems[i - 1];

      return [
        ...prevItems,
        {
          url: `${prevItem?.url ?? ''}/${slug}`,
          label: i % 2 ? slug : startCase(slug),
        },
      ];
    }, []);
  }
}
