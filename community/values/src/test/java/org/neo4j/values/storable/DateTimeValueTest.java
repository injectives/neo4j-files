/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.values.storable;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.exceptions.TemporalParseException;
import org.neo4j.exceptions.UnsupportedTemporalUnitException;

import static java.time.ZoneOffset.UTC;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.neo4j.values.storable.AssertingStructureBuilder.asserting;
import static org.neo4j.values.storable.DateTimeValue.builder;
import static org.neo4j.values.storable.DateTimeValue.datetime;
import static org.neo4j.values.storable.DateTimeValue.parse;
import static org.neo4j.values.storable.DateValue.date;
import static org.neo4j.values.storable.FrozenClock.assertEqualTemporal;
import static org.neo4j.values.storable.InputMappingStructureBuilder.fromValues;
import static org.neo4j.values.storable.LocalDateTimeValue.localDateTime;
import static org.neo4j.values.storable.LocalTimeValue.localTime;
import static org.neo4j.values.storable.TimeValue.time;
import static org.neo4j.values.storable.TimeValueTest.inUTC;
import static org.neo4j.values.storable.TimeValueTest.orFail;
import static org.neo4j.values.storable.Values.stringValue;
import static org.neo4j.values.utils.AnyValueTestUtil.assertEqual;
import static org.neo4j.values.utils.AnyValueTestUtil.assertNotEqual;

class DateTimeValueTest
{
    private FrozenClock clock = new FrozenClock( "UTC" );

    @Test
    void shouldParseDateTime()
    {
        assertEquals(
                datetime( date( 2017, 12, 17 ), time( 17, 14, 35, 123456789, UTC ) ),
                parse( "2017-12-17T17:14:35.123456789", inUTC ) );
        assertEquals(
                datetime( date( 2017, 12, 17 ), time( 17, 14, 35, 123456789, UTC ) ),
                parse( "2017-12-17T17:14:35.123456789Z", orFail ) );
        assertEquals(
                datetime( date( 2017, 12, 17 ), time( 17, 14, 35, 123456789, UTC ) ),
                parse( "2017-12-17T17:14:35.123456789+0000", orFail ) );
        assertEquals(
                datetime( date( 10000, 12, 17 ), time( 17, 14, 35, 123456789, UTC ) ),
                parse( "+10000-12-17T17:14:35.123456789+0000", orFail ) );
        assertEquals(
                datetime( date( -1, 12, 17 ), time( 17, 14, 35, 123456789, UTC ) ),
                parse( "-1-12-17T17:14:35.123456789+0000", orFail ) );
        assertEquals(
                datetime( date( 2015, 2, 1 ), time( 5, 6, 7, 123456789, UTC ) ),
                parse( "2015-02T5:6:7.123456789Z", orFail ) );
        assertEquals(
                datetime( date( 2015, 2, 1 ), time( 5, 6, 7, 123456789, UTC ) ),
                parse( "2015-032T5:6:7.123456789Z", orFail ) );

        assertEquals(
                datetime( date( 2017, 12, 17 ), time( 17, 14, 35, 123456789, UTC ) ),
                parse( "2017-12-17T17:14:35,123456789", inUTC ) );
        assertEquals(
                datetime( date( 2017, 12, 17 ), time( 17, 14, 35, 123456789, UTC ) ),
                parse( "2017-12-17T17:14:35,123456789Z", orFail ) );
        assertEquals(
                datetime( date( 2017, 12, 17 ), time( 17, 14, 35, 123456789, UTC ) ),
                parse( "2017-12-17T17:14:35,123456789+0000", orFail ) );
        assertEquals(
                datetime( date( 10000, 12, 17 ), time( 17, 14, 35, 123456789, UTC ) ),
                parse( "+10000-12-17T17:14:35,123456789+0000", orFail ) );
        assertEquals(
                datetime( date( -1, 12, 17 ), time( 17, 14, 35, 123456789, UTC ) ),
                parse( "-1-12-17T17:14:35,123456789+0000", orFail ) );
        assertEquals(
                datetime( date( 2015, 2, 1 ), time( 5, 6, 7, 123456789, UTC ) ),
                parse( "2015-02T5:6:7,123456789Z", orFail ) );
        assertEquals(
                datetime( date( 2015, 2, 1 ), time( 5, 6, 7, 123456789, UTC ) ),
                parse( "2015-032T5:6:7,123456789Z", orFail ) );
    }

    @Test
    void shouldRejectInvalidDateTimeString()
    {
        // Wrong year
        assertThrows( TemporalParseException.class, () -> parse( "10000-12-17T17:14:35", inUTC ) );
        assertThrows( TemporalParseException.class, () -> parse( "10000-12-17T17:14:35Z", orFail ) );

        // Wrong month
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-13-17T17:14:35", inUTC ) ).getMessage() ).startsWith(
                "Invalid value for MonthOfYear" );
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-00-17T17:14:35", inUTC ) ).getMessage() ).startsWith(
                "Invalid value for MonthOfYear" );
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-13-17T17:14:35Z", orFail ) ).getMessage() ).startsWith(
                "Invalid value for MonthOfYear" );
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-00-17T17:14:35Z", orFail ) ).getMessage() ).startsWith(
                "Invalid value for MonthOfYear" );

        // Wrong day of month
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-12-32T17:14:35", inUTC ) ).getMessage() ).startsWith(
                "Invalid value for DayOfMonth" );
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-12-00T17:14:35", inUTC ) ).getMessage() ).startsWith(
                "Invalid value for DayOfMonth" );
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-12-32T17:14:35Z", orFail ) ).getMessage() ).startsWith(
                "Invalid value for DayOfMonth" );
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-12-00T17:14:35Z", orFail ) ).getMessage() ).startsWith(
                "Invalid value for DayOfMonth" );

        // Wrong hour
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-12-17T24:14:35", inUTC ) ).getMessage() ).startsWith(
                "Invalid value for HourOfDay" );
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-12-17T24:14:35Z", orFail ) ).getMessage() ).startsWith(
                "Invalid value for HourOfDay" );

        // Wrong minute
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-12-17T17:60:35", inUTC ) ).getMessage() ).startsWith(
                "Invalid value for MinuteOfHour" );
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-12-17T17:60:35Z", orFail ) ).getMessage() ).startsWith(
                "Invalid value for MinuteOfHour" );

        // Wrong second
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-12-17T17:14:61", inUTC ) ).getMessage() ).startsWith(
                "Invalid value for SecondOfMinute" );
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2017-12-17T17:14:61Z", orFail ) ).getMessage() ).startsWith(
                "Invalid value for SecondOfMinute" );

        // Format is ambiguous between year+month or ordinal date
        assertThat( assertThrows( TemporalParseException.class, () -> parse( "2015-2T12:00:00", inUTC ) ).getMessage() ).startsWith(
                "Text cannot be parsed to a Date" );
    }

    @Test
    void shouldWriteDateTime()
    {
        // given
        for ( DateTimeValue value : new DateTimeValue[] {
                datetime( date( 2017, 3, 26 ), localTime( 1, 0, 0, 0 ), ZoneId.of( "Europe/Stockholm" ) ),
                datetime( date( 2017, 3, 26 ), localTime( 2, 0, 0, 0 ), ZoneId.of( "Europe/Stockholm" ) ),
                datetime( date( 2017, 3, 26 ), localTime( 3, 0, 0, 0 ), ZoneId.of( "Europe/Stockholm" ) ),
                datetime( date( 2017, 10, 29 ), localTime( 2, 0, 0, 0 ), ZoneId.of( "Europe/Stockholm" ) ),
                datetime( date( 2017, 10, 29 ), localTime( 3, 0, 0, 0 ), ZoneId.of( "Europe/Stockholm" ) ),
                datetime( date( 2017, 10, 29 ), localTime( 4, 0, 0, 0 ), ZoneId.of( "Europe/Stockholm" ) ),
        } )
        {
            List<DateTimeValue> values = new ArrayList<>( 1 );
            ValueWriter<RuntimeException> writer = new ThrowingValueWriter.AssertOnly()
            {
                @Override
                public void writeDateTime( ZonedDateTime zonedDateTime )
                {
                    values.add( datetime( zonedDateTime ) );
                }
            };

            // when
            value.writeTo( writer );

            // then
            assertEquals( singletonList( value ), values );
        }
    }

    @Test
    void shouldAcquireCurrentDateTime()
    {
        clock = new FrozenClock( "Europe/Stockholm" );
        assertEqualTemporal(
                datetime( ZonedDateTime.now( clock ) ),
                DateTimeValue.now( clock ) );

        assertEqualTemporal( // Using the named UTC timezone
                datetime( ZonedDateTime.now( clock.withZone( "UTC" ) ) ),
                DateTimeValue.now( clock, "UTC" ) );

        assertEqualTemporal( // Using the timezone defined as 0 hours offset from UTC
                datetime( ZonedDateTime.now( clock.withZone( UTC ) ) ),
                DateTimeValue.now( clock, "Z" ) );
    }

    @ParameterizedTest
    @ValueSource( strings = {"Europe/Stockholm", "America/Los_Angeles"} )
    void shouldCopyDateTime( String zone )
    {
        clock = new FrozenClock( zone );
        assertEqualTemporal(
            datetime( ZonedDateTime.now( clock ) ),
            builder( clock ).add( "datetime", datetime( ZonedDateTime.now( clock ) ) ).build() );
        assertEqualTemporal(
            datetime( ZonedDateTime.now( clock ) ),
            builder( clock )
                .add( "datetime", localDateTime( LocalDateTime.now( clock ) ) )
                .build() );
        assertEqualTemporal(
            datetime( ZonedDateTime.now( clock ).withZoneSameLocal( ZoneId.of( "America/New_York" ) ) ),
            builder( clock )
                .add( "datetime", localDateTime( LocalDateTime.now( clock ) ) )
                .add( "timezone", stringValue( "America/New_York" ) )
                .build() );
    }

    @Test
    void shouldConstructDateTimeFromComponents()
    {
        clock = new FrozenClock( "Europe/Stockholm" );
        assertEqualTemporal(
                parse( "2018-01-10T10:35:57", clock::getZone ),
                fromValues( builder( clock ) )
                        .add( "year", 2018 )
                        .add( "month", 1 )
                        .add( "day", 10 )
                        .add( "hour", 10 )
                        .add( "minute", 35 )
                        .add( "second", 57 )
                        .build() );
        assertEqualTemporal(
                parse( "2018-01-10T10:35:57.999999999", clock::getZone ),
                fromValues( builder( clock ) )
                        .add( "year", 2018 )
                        .add( "month", 1 )
                        .add( "day", 10 )
                        .add( "hour", 10 )
                        .add( "minute", 35 )
                        .add( "second", 57 )
                        .add( "nanosecond", 999999999 )
                        .build() );
        assertEqualTemporal(
                parse( "2018-01-10T10:35:57.999999", clock::getZone ),
                fromValues( builder( clock ) )
                        .add( "year", 2018 )
                        .add( "month", 1 )
                        .add( "day", 10 )
                        .add( "hour", 10 )
                        .add( "minute", 35 )
                        .add( "second", 57 )
                        .add( "microsecond", 999999 )
                        .build() );
        assertEqualTemporal(
                parse( "2018-01-10T10:35:57.999", clock::getZone ),
                fromValues( builder( clock ) )
                        .add( "year", 2018 )
                        .add( "month", 1 )
                        .add( "day", 10 )
                        .add( "hour", 10 )
                        .add( "minute", 35 )
                        .add( "second", 57 )
                        .add( "millisecond", 999 )
                        .build() );
        assertEqualTemporal(
                parse( "2018-01-10T10:35:57.001999999", clock::getZone ),
                fromValues( builder( clock ) )
                        .add( "year", 2018 )
                        .add( "month", 1 )
                        .add( "day", 10 )
                        .add( "hour", 10 )
                        .add( "minute", 35 )
                        .add( "second", 57 )
                        .add( "millisecond", 1 )
                        .add( "microsecond", 999 )
                        .add( "nanosecond", 999 )
                        .build() );
        assertEqualTemporal(
                parse( "2018-01-10T10:35:57.000001999", clock::getZone ),
                fromValues( builder( clock ) )
                        .add( "year", 2018 )
                        .add( "month", 1 )
                        .add( "day", 10 )
                        .add( "hour", 10 )
                        .add( "minute", 35 )
                        .add( "second", 57 )
                        .add( "microsecond", 1 )
                        .add( "nanosecond", 999 )
                        .build() );
        assertEqualTemporal(
                parse( "2018-01-10T10:35:57.001999", clock::getZone ),
                fromValues( builder( clock ) )
                        .add( "year", 2018 )
                        .add( "month", 1 )
                        .add( "day", 10 )
                        .add( "hour", 10 )
                        .add( "minute", 35 )
                        .add( "second", 57 )
                        .add( "millisecond", 1 )
                        .add( "microsecond", 999 )
                        .build() );
        assertEqualTemporal(
                parse( "2018-01-10T10:35:57.001999999", clock::getZone ),
                fromValues( builder( clock ) )
                        .add( "year", 2018 )
                        .add( "month", 1 )
                        .add( "day", 10 )
                        .add( "hour", 10 )
                        .add( "minute", 35 )
                        .add( "second", 57 )
                        .add( "millisecond", 1 )
                        .add( "microsecond", 999 )
                        .add( "nanosecond", 999 )
                        .build() );
    }

    @Test
    void shouldRejectInvalidFieldCombinations()
    {
        asserting( fromValues( builder( clock ) ) )
                .add( "year", 2018 )
                .add( "month", 12 )
                .add( "dayOfWeek", 5 )
                .assertThrows( UnsupportedTemporalUnitException.class, "Cannot assign dayOfWeek to calendar date." );
        asserting( fromValues( builder( clock ) ) )
                .add( "year", 2018 )
                .add( "week", 12 )
                .add( "day", 12 )
                .assertThrows( UnsupportedTemporalUnitException.class, "Cannot assign day to week date." );
        asserting( fromValues( builder( clock ) ) )
                .add( "year", 2018 )
                .add( "ordinalDay", 12 )
                .add( "dayOfWeek", 1 )
                .assertThrows( UnsupportedTemporalUnitException.class, "Cannot assign dayOfWeek to ordinal date." );
        asserting( fromValues( builder( clock ) ) )
                .add( "year", 2018 )
                .add( "month", 1 )
                .add( "day", 10 )
                .add( "hour", 10 )
                .add( "minute", 35 )
                .add( "second", 57 )
                .add( "nanosecond", 1000000000 )
                .assertThrows( InvalidArgumentException.class, "Invalid value for Nanosecond: 1000000000" );
        asserting( fromValues( builder( clock ) ))
                .add( "year", 2018 )
                .add( "month", 1 )
                .add( "day", 10 )
                .add( "hour", 10 )
                .add( "minute", 35 )
                .add( "second", 57 )
                .add( "microsecond", 1000000 )
                .assertThrows( InvalidArgumentException.class, "Invalid value for Microsecond: 1000000" );
        asserting( fromValues( builder( clock ) ))
                .add( "year", 2018 )
                .add( "month", 1 )
                .add( "day", 10 )
                .add( "hour", 10 )
                .add( "minute", 35 )
                .add( "second", 57 )
                .add( "millisecond", 1000 )
                .assertThrows( InvalidArgumentException.class, "Invalid value for Millisecond: 1000" );
        asserting( fromValues( builder( clock ) ))
                .add( "year", 2018 )
                .add( "month", 1 )
                .add( "day", 10 )
                .add( "hour", 10 )
                .add( "minute", 35 )
                .add( "second", 57 )
                .add( "millisecond", 1 )
                .add( "nanosecond", 1000000 )
                .assertThrows( InvalidArgumentException.class, "Invalid value for Nanosecond: 1000000" );
        asserting( fromValues( builder( clock ) ))
                .add( "year", 2018 )
                .add( "month", 1 )
                .add( "day", 10 )
                .add( "hour", 10 )
                .add( "minute", 35 )
                .add( "second", 57 )
                .add( "microsecond", 1 )
                .add( "nanosecond", 1000 )
                .assertThrows( InvalidArgumentException.class, "Invalid value for Nanosecond: 1000" );
        asserting( fromValues( builder( clock ) ))
                .add( "year", 2018 )
                .add( "month", 1 )
                .add( "day", 10 )
                .add( "hour", 10 )
                .add( "minute", 35 )
                .add( "second", 57 )
                .add( "millisecond", 1 )
                .add( "microsecond", 1000 )
                .assertThrows( InvalidArgumentException.class, "Invalid value for Microsecond: 1000" );
        asserting( fromValues( builder( clock ) ))
                .add( "year", 2018 )
                .add( "month", 1 )
                .add( "day", 10 )
                .add( "hour", 10 )
                .add( "minute", 35 )
                .add( "second", 57 )
                .add( "millisecond", 1 )
                .add( "microsecond", 1000 )
                .add( "nanosecond", 999 )
                .assertThrows( InvalidArgumentException.class, "Invalid value for Microsecond: 1000" );
        asserting( fromValues( builder( clock ) ))
                .add( "year", 2018 )
                .add( "month", 1 )
                .add( "day", 10 )
                .add( "hour", 10 )
                .add( "minute", 35 )
                .add( "second", 57 )
                .add( "millisecond", 1 )
                .add( "microsecond", 999 )
                .add( "nanosecond", 1000 )
                .assertThrows( InvalidArgumentException.class, "Invalid value for Nanosecond: 1000" );
    }

    @Test
    void shouldRejectInvalidComponentValues()
    {
        asserting( fromValues( builder( clock ) ) ).add( "year", 2018 ).add( "moment", 12 ).assertThrows( InvalidArgumentException.class,
                "No such field: moment" );
        asserting( fromValues( builder( clock ) ) ).add( "year", 2018 ).add( "month", 12 ).add( "day", 5 ).add( "hour", 5 ).add( "minute", 5 ).add( "second",
                5 ).add( "picosecond", 12 ).assertThrows( InvalidArgumentException.class, "No such field: picosecond" );
    }

    @Test
    void shouldAddDurationToDateTimes()
    {
        assertEquals( datetime( date( 2018, 2, 1 ), time( 1, 17, 3, 0, UTC ) ),
                datetime( date( 2018, 1, 1 ), time( 1, 2, 3, 0, UTC ) ).add( DurationValue.duration( 1, 0, 900, 0 ) ) );
        assertEquals( datetime( date( 2018, 2, 28 ), time( 0, 0, 0, 0, UTC ) ),
                datetime( date( 2018, 1, 31 ), time( 0, 0, 0, 0, UTC ) ).add( DurationValue.duration( 1, 0, 0, 0 ) ) );
        assertEquals( datetime( date( 2018, 1, 28 ), time( 0, 0, 0, 0, UTC ) ),
                datetime( date( 2018, 2, 28 ), time( 0, 0, 0, 0, UTC ) ).add( DurationValue.duration( -1, 0, 0, 0 ) ) );
    }

    @Test
    void shouldReuseInstanceInArithmetics()
    {
        final DateTimeValue datetime = datetime( date( 2018, 2, 1 ), time( 1, 17, 3, 0, UTC ) );
        assertSame( datetime,
                datetime.add( DurationValue.duration( 0, 0, 0, 0 ) ) );
    }

    @Test
    void shouldSubtractDurationFromDateTimes()
    {
        assertEquals( datetime( date( 2018, 1, 1 ), time( 1, 2, 3, 0, UTC ) ),
                datetime( date( 2018, 2, 1 ), time( 1, 17, 3, 0, UTC ) ).sub( DurationValue.duration( 1, 0, 900, 0 ) ) );
        assertEquals( datetime( date( 2018, 1, 28 ), time( 0, 0, 0, 0, UTC ) ),
                datetime( date( 2018, 2, 28 ), time( 0, 0, 0, 0, UTC ) ).sub( DurationValue.duration( 1, 0, 0, 0 ) ) );
        assertEquals( datetime( date( 2018, 2, 28 ), time( 0, 0, 0, 0, UTC ) ),
                datetime( date( 2018, 1, 31 ), time( 0, 0, 0, 0, UTC ) ).sub( DurationValue.duration( -1, 0, 0, 0 ) ) );
    }

    @Test
    void shouldEqualItself()
    {
        assertEqual( datetime( 10000, 100, UTC ), datetime( 10000, 100, UTC ) );
    }

    @Disabled // only runnable it JVM supports East-Saskatchewan
    void shouldEqualRenamedTimeZone()
    {
        assertEqual( datetime( 10000, 100, ZoneId.of( "Canada/Saskatchewan" ) ),
                     datetime( 10000, 100, ZoneId.of( "Canada/East-Saskatchewan" ) ) );
    }

    @Test
    void shouldNotEqualSameInstantButDifferentTimezone()
    {
        assertNotEqual( datetime( 10000, 100, UTC ), datetime( 10000, 100, ZoneOffset.of( "+01:00" ) ) );
    }

    @Test
    void shouldNotEqualSameInstantInSameLocalTimeButDifferentTimezone()
    {
        assertNotEqual( datetime( 2018, 1, 31, 10, 52, 5, 6, UTC ), datetime( 2018, 1, 31, 11, 52, 5, 6, "+01:00" ) );
    }

    @Test
    void shouldNotEqualSameInstantButDifferentTimezoneWithSameOffset()
    {
        assertNotEqual( datetime( 1969, 12, 31, 23, 59, 59, 0, UTC ), datetime(1969, 12, 31, 23, 59, 59, 0, "Africa/Freetown" ) );
    }
}
