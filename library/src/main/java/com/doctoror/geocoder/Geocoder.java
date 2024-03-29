/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2015 Yaroslav Mytkalyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.doctoror.geocoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.annotation.NonNull;

/**
 * A class for handling geocoding and reverse geocoding. Geocoding is the
 * process of transforming a street address or other description of a location
 * into a (latitude, longitude) coordinate. Reverse geocoding is the process of
 * transforming a (latitude, longitude) coordinate into a (partial) address. The
 * amount of detail in a reverse geocoded location description may vary, for
 * example one might contain the full street address of the closest building,
 * while another might contain only a city name and postal code.
 *
 * This is a replacement of built-in Geocoder which is not always available.
 *
 * For more information visit https://developers.google.com/maps/documentation/geocoding/
 */
public final class Geocoder {

    private static final String PREFERENCES_GEOCODER = "com.doctoror.geocoder.preferences";

    private static final String KEY_ALLOW = "com.doctoror.geocoder.preferences.keys.allow";

    private final Context mContext;

    private final Locale mLocale;

    private SharedPreferences mSharedPreferences;

    private long mAllowedDate;

    /**
     * Constructs a Geocoder whose responses will be localized for the default
     * system Locale.
     *
     * @param context the Context of the calling Activity
     */
    public Geocoder(final Context context, final Locale locale) {
        if (context == null) {
            throw new NullPointerException("context == null");
        }
        if (locale == null) {
            throw new NullPointerException("locale == null");
        }
        mContext = context;
        mLocale = locale;
    }

    /**
     * Returns an array of Addresses that are known to describe the area
     * immediately surrounding the given latitude and longitude. The returned
     * addresses will be localized for the locale provided to this class's
     * constructor.
     *
     * <p>
     * The returned values may be obtained by means of a network lookup. The
     * results are a best guess and are not guaranteed to be meaningful or
     * correct. It may be useful to call this method from a thread separate from
     * your primary UI thread.
     *
     * @param latitude               the latitude a point for the search
     * @param longitude              the longitude a point for the search
     * @param maxResults             max number of addresses to return. Smaller numbers (1 to 5)
     *                               are recommended
     * @param parseAddressComponents If set to true, will parse "address_components". For more
     *                               details see documentation provided in the Geocoder class
     *                               javadoc
     * @return a list of Address objects. Returns empty list if no matches were found.
     * @throws IllegalArgumentException if latitude is less than -90 or greater than 90
     * @throws IllegalArgumentException if longitude is less than -180 or greater than 180
     * @throws IOException              if the network is unavailable or any other I/O problem
     *                                  occurs
     */
    @NonNull
    public List<Address> getFromLocation(final double latitude, final double longitude,
            final int maxResults, final boolean parseAddressComponents)
            throws IOException, LimitExceededException {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude == " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude == " + longitude);
        }

        if (isLimitExceeded()) {
            throw new LimitExceededException();
        }

        final List<Address> results = new ArrayList<>();

        final StringBuilder url = new StringBuilder(
                "http://maps.googleapis.com/maps/api/geocode/json?sensor=true&latlng=");
        url.append(latitude);
        url.append(',');
        url.append(longitude);
        url.append("&language=");
        url.append(mLocale.getLanguage());

        final byte[] data = download(url.toString());
        if (data != null) {
            Parser.parseJson(results, maxResults, data, parseAddressComponents);
        }
        return results;
    }

    /**
     * Returns an array of Addresses that are known to describe the named
     * location, which may be a place name such as "Dalvik,
     * Iceland", an address such as "1600 Amphitheatre Parkway, Mountain View,
     * CA", an airport code such as "SFO", etc.. The returned addresses will be
     * localized for the locale provided to this class's constructor.
     *
     * <p>
     * The query will block and returned values will be obtained by means of a
     * network lookup. The results are a best guess and are not guaranteed to be
     * meaningful or correct. It may be useful to call this method from a thread
     * separate from your primary UI thread.
     *
     * @param locationName           a user-supplied description of a location
     * @param maxResults             max number of results to return. Smaller numbers (1 to 5) are
     *                               recommended
     * @param parseAddressComponents If set to true, will parse "address_components". For more
     *                               details see documentation provided in the Geocoder class
     *                               javadoc
     * @return a list of Address objects. Returns empty list if no matches were found.
     * @throws IllegalArgumentException if locationName is null
     * @throws IOException              if the network is unavailable or any other I/O problem
     *                                  occurs
     */
    @NonNull
    public List<Address> getFromLocationName(final String locationName, final int maxResults,
            final boolean parseAddressComponents)
            throws IOException, LimitExceededException {
        if (locationName == null) {
            throw new IllegalArgumentException("locationName == null");
        }

        if (isLimitExceeded()) {
            throw new LimitExceededException();
        }

        final List<Address> results = new ArrayList<>();

        final StringBuilder request = new StringBuilder(
                "http://maps.googleapis.com/maps/api/geocode/json?sensor=false");
        request.append("&language=").append(mLocale.getLanguage());
        request.append("&address=").append(
                URLEncoder.encode(locationName, "UTF-8"));

        byte[] data = download(request.toString());
        if (data != null) {
            try {
                Parser.parseJson(results, maxResults, data, parseAddressComponents);
            } catch (LimitExceededException e) {
                //LimitExceededException could be thrown if too many calls per second
                //If after two seconds, it is thrown again - then it means there are too much calls per 24 hours
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                    return results;
                }
                data = download(request.toString());
                if (data != null) {
                    try {
                        Parser.parseJson(results, maxResults, data, parseAddressComponents);
                    } catch (LimitExceededException lee) {
                        // available in 24 hours
                        setAllowedDate(System.currentTimeMillis() + 86400000L);
                        throw lee;
                    }
                }
            }
        }
        return results;
    }

    /**
     * Downloads data to buffer
     *
     * @param url Data location
     * @return downloaded data or null if error occurred
     */
    private static byte[] download(String url) {
        InputStream is = null;
        ByteArrayOutputStream os = null;

        try {

            final URL u = new URL(url);
            final URLConnection connection = u.openConnection();
            connection.connect();

            is = connection.getInputStream();
            os = new ByteArrayOutputStream();

            final byte[] buffer = new byte[4096];
            int read;

            while (true) {
                read = is.read(buffer, 0, buffer.length);
                if (read == -1) {
                    break;
                }
                os.write(buffer, 0, read);
            }

            return os.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
            }
        }

        return null;
    }

    /**
     * Returns true if limit is exceeded and next query is not allowed
     *
     * @return true if limit is exceeded and next query is not allowed; false
     * otherwise
     */
    private boolean isLimitExceeded() {
        return System.currentTimeMillis() <= getAllowedDate();
    }

    /**
     * Sets date after which next geocoding query is allowed
     *
     * @param date the date after which next geocoding query is allowed
     */
    private void setAllowedDate(final long date) {
        mAllowedDate = date;
        if (mSharedPreferences == null) {
            mSharedPreferences = mContext.getSharedPreferences(
                    PREFERENCES_GEOCODER, Context.MODE_PRIVATE);
        }
        final Editor e = mSharedPreferences.edit();
        e.putLong(KEY_ALLOW, date);
        e.apply();
    }

    /**
     * Returns date after which the next geocoding query is allowed
     *
     * @return date after which the next geocoding query is allowed
     */
    private long getAllowedDate() {
        if (mSharedPreferences == null) {
            mSharedPreferences = mContext
                    .getSharedPreferences(PREFERENCES_GEOCODER, Context.MODE_PRIVATE);
            mAllowedDate = mSharedPreferences.getLong(KEY_ALLOW, 0);
        }
        return mAllowedDate;
    }

    /**
     * Is thrown when the query was over limit before 24 hours
     */
    public static final class LimitExceededException
            extends Exception {

        private static final long serialVersionUID = -1243645207607944474L;
    }

}