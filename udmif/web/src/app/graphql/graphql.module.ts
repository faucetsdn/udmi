import { NgModule } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';
import { ApolloModule, APOLLO_OPTIONS } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';
import { InMemoryCache, ApolloClientOptions, ApolloLink } from '@apollo/client/core';
import { setContext } from '@apollo/client/link/context';
import { SocialAuthService } from '@abacritt/angularx-social-login';
import { firstValueFrom, map, take } from 'rxjs';

const uri = '/api';
export function createApollo(httpLink: HttpLink, authService: SocialAuthService): ApolloClientOptions<any> {
  const auth = setContext(async (operation, context) => {
    const token = await firstValueFrom(
      authService.authState.pipe(
        map((authState) => authState?.authToken),
        take(1)
      )
    );

    // TODO:: check refreshToken,
    // see: https://apollo-angular.com/docs/recipes/authentication/#waiting-for-a-refreshed-token

    if (!token) {
      return {};
    } else {
      return {
        headers: {
          Authorization: `Bearer ${token}`,
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
      useFactory: createApollo,
      deps: [HttpLink, SocialAuthService],
    },
  ],
})
export class GraphQLModule {}
