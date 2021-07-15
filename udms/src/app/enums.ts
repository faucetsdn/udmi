export enum State {
    unknown,
    broken,
    active,
    damaged,
    down,
    healthy,
    inactive,
    initializing,
    split,
    up
}

export enum SystemType {
    DAQ = 'DAQ',
    UDMS = 'UDMS'
}
