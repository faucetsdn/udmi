import { NgModule } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';
import { ApolloModule, APOLLO_OPTIONS } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';
import { InMemoryCache, ApolloClientOptions, ApolloLink } from '@apollo/client/core';
import { setContext } from '@apollo/client/link/context';
import { SocialAuthService } from '@abacritt/angularx-social-login';
import { firstValueFrom, map, take } from 'rxjs';

const uri = '/api';
export function ApolloFactory(httpLink: HttpLink, authService: SocialAuthService): ApolloClientOptions<any> {
  const auth = setContext(async (operation, context) => {
    const idToken = await firstValueFrom(
      authService.authState.pipe(
        map((authState) => authState?.idToken),
        take(1)
      )
    );

    // If refreshToken needs to be used see:
    // https://apollo-angular.com/docs/recipes/authentication/#waiting-for-a-refreshed-token

    if (!idToken) {
      return {};
    } else {
      return {
        headers: {
          idToken,
        },
      };
    }
  });

  return {
    link: ApolloLink.from([auth, httpLink.create({ uri })]),
    cache: new InMemoryCache(),
  };
}

@NgModule({
  imports: [ApolloModule, HttpClientModule],
  providers: [
    {
      provide: APOLLO_OPTIONS,
      useFactory: ApolloFactory,
      deps: [HttpLink, SocialAuthService],
    },
  ],
})
export class GraphQLModule {}
