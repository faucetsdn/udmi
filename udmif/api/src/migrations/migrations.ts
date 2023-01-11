import { Knex } from 'knex';

export async function up(knex: Knex): Promise<any> {
  await knex.raw('create extension if not exists "uuid-ossp"');

  await knex.schema.createTable('devices', function (table) {
    table.uuid('uuid').defaultTo(knex.raw('uuid_generate_v4()')).primary();
    table.string('id', 255).notNullable();
    table.string('name', 255).notNullable();
    table.string('site', 255).notNullable();
    table.string('make', 255);
    table.string('model', 255);
    table.string('section', 255);
    table.string('lastPayload', 255);
    table.boolean('operational');
    table.string('serialNumber', 255);
    table.string('firmware', 255);
    table.string('lastTelemetryUpdated', 255);
    table.string('lastStateUpdated', 255);
    table.string('lastTelemetrySaved', 255);
    table.string('lastStateSaved', 255);
    table.jsonb('validation');
    table.jsonb('points');
    table.jsonb('tags');
    table.unique(['name', 'site']);
  });

  await knex.schema.createTable('sites', function (table) {
    table.uuid('uuid').defaultTo(knex.raw('uuid_generate_v4()')).primary();
    table.string('name', 255).notNullable();
    table.jsonb('validation');
    table.unique(['name']);
  });

  await knex.schema.createTable('device_validations', function (table) {
    table.uuid('uuid').defaultTo(knex.raw('uuid_generate_v4()')).primary();
    table.string('timestamp', 255).notNullable();
    table.jsonb('deviceKey').notNullable();
    table.jsonb('message').notNullable();
    table.unique(['timestamp', 'deviceKey']);
  });

  await knex.schema.createTable('site_validations', function (table) {
    table.uuid('uuid').defaultTo(knex.raw('uuid_generate_v4()')).primary();
    table.string('timestamp', 255).notNullable();
    table.string('siteName', 255).notNullable();
    table.jsonb('message');
    table.unique(['timestamp', 'siteName']);
  });
}

export async function down(knex: Knex): Promise<any> {
  return;
}
