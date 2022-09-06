import { merge } from 'lodash';
import { resolvers as commonResolvers } from '../common/resolvers';
import { resolvers as deviceResolvers } from '../device/resolvers';
import { resolvers as siteResolvers } from '../site/resolvers';

export const resolvers = merge(commonResolvers, deviceResolvers, siteResolvers);
