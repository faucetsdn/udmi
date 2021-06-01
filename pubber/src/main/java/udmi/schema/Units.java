
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Taken from standard BACnet engineering units
 * 
 */
@Generated("jsonschema2pojo")
public enum Units {

    SQUARE_METERS("Square-meters"),
    SQUARE_FEET("Square-feet"),
    MILLIAMPERES("Milliamperes"),
    AMPERES("Amperes"),
    OHMS("Ohms"),
    VOLTS("Volts"),
    KILO_VOLTS("Kilo-volts"),
    MEGA_VOLTS("Mega-volts"),
    VOLT_AMPERES("Volt-amperes"),
    KILO_VOLT_AMPERES("Kilo-volt-amperes"),
    MEGA_VOLT_AMPERES("Mega-volt-amperes"),
    VOLT_AMPERES_REACTIVE("Volt-amperes-reactive"),
    KILO_VOLT_AMPERES_REACTIVE("Kilo-volt-amperes-reactive"),
    MEGA_VOLT_AMPERES_REACTIVE("Mega-volt-amperes-reactive"),
    DEGREES_PHASE("Degrees-phase"),
    POWER_FACTOR("Power-factor"),
    JOULES("Joules"),
    KILOJOULES("Kilojoules"),
    WATT_HOURS("Watt-hours"),
    KILOWATT_HOURS("Kilowatt-hours"),
    BT_US("BTUs"),
    THERMS("Therms"),
    TON_HOURS("Ton-hours"),
    JOULES_PER_KILOGRAM_DRY_AIR("Joules-per-kilogram-dry-air"),
    BT_US_PER_POUND_DRY_AIR("BTUs-per-pound-dry-air"),
    CYCLES_PER_HOUR("Cycles-per-hour"),
    CYCLES_PER_MINUTE("Cycles-per-minute"),
    HERTZ("Hertz"),
    GRAMS_OF_WATER_PER_KILOGRAM_DRY_AIR("Grams-of-water-per-kilogram-dry-air"),
    PERCENT_RELATIVE_HUMIDITY("Percent-relative-humidity"),
    MILLIMETERS("Millimeters"),
    METERS("Meters"),
    INCHES("Inches"),
    FEET("Feet"),
    WATTS_PER_SQUARE_FOOT("Watts-per-square-foot"),
    WATTS_PER_SQUARE_METER("Watts-per-square-meter"),
    LUMENS("Lumens"),
    LUXES("Luxes"),
    FOOT_CANDLES("Foot-candles"),
    KILOGRAMS("Kilograms"),
    POUNDS_MASS("Pounds-mass"),
    TONS("Tons"),
    KILOGRAMS_PER_SECOND("Kilograms-per-second"),
    KILOGRAMS_PER_MINUTE("Kilograms-per-minute"),
    KILOGRAMS_PER_HOUR("Kilograms-per-hour"),
    POUNDS_MASS_PER_MINUTE("Pounds-mass-per-minute"),
    POUNDS_MASS_PER_HOUR("Pounds-mass-per-hour"),
    WATTS("Watts"),
    KILOWATTS("Kilowatts"),
    MEGAWATTS("Megawatts"),
    BT_US_PER_HOUR("BTUs-per-hour"),
    HORSEPOWER("Horsepower"),
    TONS_REFRIGERATION("Tons-refrigeration"),
    PASCALS("Pascals"),
    KILOPASCALS("Kilopascals"),
    BARS("Bars"),
    POUNDS_FORCE_PER_SQUARE_INCH("Pounds-force-per-square-inch"),
    CENTIMETERS_OF_WATER("Centimeters-of-water"),
    INCHES_OF_WATER("Inches-of-water"),
    MILLIMETERS_OF_MERCURY("Millimeters-of-mercury"),
    CENTIMETERS_OF_MERCURY("Centimeters-of-mercury"),
    INCHES_OF_MERCURY("Inches-of-mercury"),
    DEGREES_CELSIUS("Degrees-Celsius"),
    DEGREES_KELVIN("Degrees-Kelvin"),
    DEGREES_FAHRENHEIT("Degrees-Fahrenheit"),
    DEGREE_DAYS_CELSIUS("Degree-days-Celsius"),
    DEGREE_DAYS_FAHRENHEIT("Degree-days-Fahrenheit"),
    YEARS("Years"),
    MONTHS("Months"),
    WEEKS("Weeks"),
    DAYS("Days"),
    HOURS("Hours"),
    MINUTES("Minutes"),
    SECONDS("Seconds"),
    METERS_PER_SECOND("Meters-per-second"),
    KILOMETERS_PER_HOUR("Kilometers-per-hour"),
    FEET_PER_SECOND("Feet-per-second"),
    FEET_PER_MINUTE("Feet-per-minute"),
    MILES_PER_HOUR("Miles-per-hour"),
    CUBIC_FEET("Cubic-feet"),
    CUBIC_METERS("Cubic-meters"),
    IMPERIAL_GALLONS("Imperial-gallons"),
    LITERS("Liters"),
    US_GALLONS("Us-gallons"),
    CUBIC_FEET_PER_MINUTE("Cubic-feet-per-minute"),
    CUBIC_METERS_PER_SECOND("Cubic-meters-per-second"),
    IMPERIAL_GALLONS_PER_MINUTE("Imperial-gallons-per-minute"),
    LITERS_PER_SECOND("Liters-per-second"),
    LITERS_PER_MINUTE("Liters-per-minute"),
    US_GALLONS_PER_MINUTE("Us-gallons-per-minute"),
    DEGREES_ANGULAR("Degrees-angular"),
    DEGREES_CELSIUS_PER_HOUR("Degrees-Celsius-per-hour"),
    DEGREES_CELSIUS_PER_MINUTE("Degrees-Celsius-per-minute"),
    DEGREES_FAHRENHEIT_PER_HOUR("Degrees-Fahrenheit-per-hour"),
    DEGREES_FAHRENHEIT_PER_MINUTE("Degrees-Fahrenheit-per-minute"),
    NO_UNITS("No-units"),
    PARTS_PER_MILLION("Parts-per-million"),
    PARTS_PER_BILLION("Parts-per-billion"),
    PERCENT("Percent"),
    PERCENT_PER_SECOND("Percent-per-second"),
    PER_MINUTE("Per-minute"),
    PER_SECOND("Per-second"),
    PSI_PER_DEGREE_FAHRENHEIT("Psi-per-Degree-Fahrenheit"),
    RADIANS("Radians"),
    REVOLUTIONS_PER_MINUTE("Revolutions-per-minute"),
    CURRENCY_1("Currency1"),
    CURRENCY_2("Currency2"),
    CURRENCY_3("Currency3"),
    CURRENCY_4("Currency4"),
    CURRENCY_5("Currency5"),
    CURRENCY_6("Currency6"),
    CURRENCY_7("Currency7"),
    CURRENCY_8("Currency8"),
    CURRENCY_9("Currency9"),
    CURRENCY_10("Currency10"),
    SQUARE_INCHES("Square-inches"),
    SQUARE_CENTIMETERS("Square-centimeters"),
    BT_US_PER_POUND("BTUs-per-pound"),
    CENTIMETERS("Centimeters"),
    POUNDS_MASS_PER_SECOND("Pounds-mass-per-second"),
    DELTA_DEGREES_FAHRENHEIT("Delta-Degrees-Fahrenheit"),
    DELTA_DEGREES_KELVIN("Delta-Degrees-Kelvin"),
    KILOHMS("Kilohms"),
    MEGOHMS("Megohms"),
    MILLIVOLTS("Millivolts"),
    KILOJOULES_PER_KILOGRAM("Kilojoules-per-kilogram"),
    MEGAJOULES("Megajoules"),
    JOULES_PER_DEGREE_KELVIN("Joules-per-degree-Kelvin"),
    JOULES_PER_KILOGRAM_DEGREE_KELVIN("Joules-per-kilogram-degree-Kelvin"),
    KILOHERTZ("Kilohertz"),
    MEGAHERTZ("Megahertz"),
    PER_HOUR("Per-hour"),
    MILLIWATTS("Milliwatts"),
    HECTOPASCALS("Hectopascals"),
    MILLIBARS("Millibars"),
    CUBIC_METERS_PER_HOUR("Cubic-meters-per-hour"),
    LITERS_PER_HOUR("Liters-per-hour"),
    KILOWATT_HOURS_PER_SQUARE_METER("Kilowatt-hours-per-square-meter"),
    KILOWATT_HOURS_PER_SQUARE_FOOT("Kilowatt-hours-per-square-foot"),
    MEGAJOULES_PER_SQUARE_METER("Megajoules-per-square-meter"),
    MEGAJOULES_PER_SQUARE_FOOT("Megajoules-per-square-foot"),
    WATTS_PER_SQUARE_METER_DEGREE_KELVIN("Watts-per-square-meter-Degree-Kelvin"),
    CUBIC_FEET_PER_SECOND("Cubic-feet-per-second"),
    PERCENT_OBSCURATION_PER_FOOT("Percent-obscuration-per-foot"),
    PERCENT_OBSCURATION_PER_METER("Percent-obscuration-per-meter"),
    MILLIOHMS("Milliohms"),
    MEGAWATT_HOURS("Megawatt-hours"),
    KILO_BT_US("Kilo-BTUs"),
    MEGA_BT_US("Mega-BTUs"),
    KILOJOULES_PER_KILOGRAM_DRY_AIR("Kilojoules-per-kilogram-dry-air"),
    MEGAJOULES_PER_KILOGRAM_DRY_AIR("Megajoules-per-kilogram-dry-air"),
    KILOJOULES_PER_DEGREE_KELVIN("Kilojoules-per-degree-Kelvin"),
    MEGAJOULES_PER_DEGREE_KELVIN("Megajoules-per-degree-Kelvin"),
    NEWTON("Newton"),
    GRAMS_PER_SECOND("Grams-per-second"),
    GRAMS_PER_MINUTE("Grams-per-minute"),
    TONS_PER_HOUR("Tons-per-hour"),
    KILO_BT_US_PER_HOUR("Kilo-BTUs-per-hour"),
    HUNDREDTHS_SECONDS("Hundredths-seconds"),
    MILLISECONDS("Milliseconds"),
    NEWTON_METERS("Newton-meters"),
    MILLIMETERS_PER_SECOND("Millimeters-per-second"),
    MILLIMETERS_PER_MINUTE("Millimeters-per-minute"),
    METERS_PER_MINUTE("Meters-per-minute"),
    METERS_PER_HOUR("Meters-per-hour"),
    CUBIC_METERS_PER_MINUTE("Cubic-meters-per-minute"),
    METERS_PER_SECOND_PER_SECOND("Meters-per-second-per-second"),
    AMPERES_PER_METER("Amperes-per-meter"),
    AMPERES_PER_SQUARE_METER("Amperes-per-square-meter"),
    AMPERE_SQUARE_METERS("Ampere-square-meters"),
    FARADS("Farads"),
    HENRYS("Henrys"),
    OHM_METERS("Ohm-meters"),
    SIEMENS("Siemens"),
    SIEMENS_PER_METER("Siemens-per-meter"),
    TESLAS("Teslas"),
    VOLTS_PER_DEGREE_KELVIN("Volts-per-degree-Kelvin"),
    VOLTS_PER_METER("Volts-per-meter"),
    WEBERS("Webers"),
    CANDELAS("Candelas"),
    CANDELAS_PER_SQUARE_METER("Candelas-per-square-meter"),
    KELVINS_PER_HOUR("Kelvins-per-hour"),
    KELVINS_PER_MINUTE("Kelvins-per-minute"),
    JOULE_SECONDS("Joule-seconds"),
    SQUARE_METERS_PER_NEWTON("Square-meters-per-Newton"),
    KILOGRAM_PER_CUBIC_METER("Kilogram-per-cubic-meter"),
    NEWTON_SECONDS("Newton-seconds"),
    NEWTONS_PER_METER("Newtons-per-meter"),
    WATTS_PER_METER_PER_DEGREE_KELVIN("Watts-per-meter-per-degree-Kelvin");
    private final String value;
    private final static Map<String, Units> CONSTANTS = new HashMap<String, Units>();

    static {
        for (Units c: values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    Units(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    @JsonValue
    public String value() {
        return this.value;
    }

    @JsonCreator
    public static Units fromValue(String value) {
        Units constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

}
