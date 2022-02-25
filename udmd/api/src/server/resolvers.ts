import { merge } from 'lodash';
import { resolvers as resolversDevice } from '../device/resolvers';

export const resolvers = merge({}, resolversDevice);
