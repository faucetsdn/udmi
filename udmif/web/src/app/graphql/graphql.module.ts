import { NgModule } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';
import { ApolloModule, APOLLO_OPTIONS } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';
import { InMemoryCache, ApolloClientOptions, ApolloLink } from '@apollo/client/core';
import { setContext } from '@apollo/client/link/context';
import { firstValueFrom, map, take } from 'rxjs';
import { EnvService } from '../env/env.service';
import { onError } from '@apollo/client/link/error';
import { AuthService } from '../auth/auth.service';

export function ApolloFactory(httpLink: HttpLink, env: EnvService, auth: AuthService): ApolloClientOptions<any> {
  const authLink = setContext(async (operation, context) => {
    const idToken = await firstValueFrom(
      auth.authState.pipe(
        map((authState) => authState?.idToken),
        take(1)
      )
    );

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

  const errorLink = onError(({ forward, networkError, operation }) => {
    if (networkError) {
      if (JSON.stringify(networkError).includes('UNAUTHENTICATED')) {
        auth.logout(); // no refresh for google, so logout
      }
    }
  });

  return {
    link: ApolloLink.from([errorLink, authLink, httpLink.create({ uri: env.apiUri })]),
    cache: new InMemoryCache({
      typePolicies: {
        Device: {
          keyFields: ['uuid'],
        },
        Site: {
          keyFields: ['uuid'],
        },
        DeviceError: {
          keyFields: ['message', 'timestamp'],
        },
      },
    }),
  };
}

@NgModule({
  imports: [ApolloModule, HttpClientModule],
  providers: [
    {
      provide: APOLLO_OPTIONS,
      useFactory: ApolloFactory,
      deps: [HttpLink, EnvService, AuthService],
    },
  ],
})
export class GraphQLModule {}
