import { Error } from "../../model/UdmiMessage";
import { Validation, ValidationBuilder } from "../../model/Validation";

describe('Validation.ValidationBuilder', () => {

    const CATEGORY_ERROR: string = 'Validation category can not be empty';
    const MESSAGE_ERROR: string = 'Validation message can not be empty';
    const TIMESTAMP_ERROR: string = 'Validation timestamp can not be empty';
    const ERRORS_ERROR: string = 'Validation errors can not be empty';

    let builder: ValidationBuilder;
    beforeEach(() => {
        builder = new ValidationBuilder();
    });

    test.each([undefined, null])('throws exception when category is not provided', (category: string) => {
        expect(() => {
            builder.category(category);
        }).toThrow(CATEGORY_ERROR);
    })

    test.each([undefined, null])('throws exception when message is not provided', (message: string) => {
        expect(() => {
            builder.message(message);
        }).toThrow(MESSAGE_ERROR);
    })

    test.each([undefined, null])('throws exception when timestamp is not provided', (timestamp: string) => {
        expect(() => {
            builder.timestamp(timestamp);
        }).toThrow(TIMESTAMP_ERROR);
    })

    test.each([undefined, null])('throws exception when errors are not provided', (error: Error[]) => {
        expect(() => {
            builder.errors(error);
        }).toThrow(ERRORS_ERROR);
    })

    test('build returns a Validation object', () => {

        const output: Validation = builder
            .category('some-category')
            .message('some-message')
            .detail('some-detail')
            .timestamp('2022-08-03T17:28:49Z')
            .errors([{
                message: "While converting to json node: 2 schema violations found",
                level: 500,
                category: "category-x"
            }]).build();

        const expectedValidation: Validation = {
            category: 'some-category',
            message: 'some-message',
            detail: 'some-detail',
            timestamp: '2022-08-03T17:28:49Z',
            errors: [{
                message: "While converting to json node: 2 schema violations found",
                level: 500,
                category: "category-x"
            }]
        };

        expect(output).toEqual(expectedValidation);
    })

});