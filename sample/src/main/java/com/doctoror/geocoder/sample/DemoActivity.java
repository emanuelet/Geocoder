/*
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

package com.doctoror.geocoder.sample;

import com.doctoror.geocoder.Address;
import com.doctoror.geocoder.Geocoder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

public final class DemoActivity extends ActionBarActivity
        implements SearchView.OnQueryTextListener, AdapterView.OnItemClickListener {

    private static final int MIN_REQUEST_LENGTH = 3;

    private static final int ANIMATOR_CHILD_CONTENT_EMPTY = 0;

    private static final int ANIMATOR_CHILD_PROGRESS = 1;

    private static final int ANIMATOR_CHILD_CONTENT = 2;

    private final DemoActivityHandler mHandler = new DemoActivityHandler(Looper.getMainLooper(),
            this);

    private ViewAnimator mViewAnimator;

    private ResultsAdapter mAdapter;

    private LoadTask mLoadTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        mViewAnimator = (ViewAnimator) findViewById(R.id.animator);

        final SearchView searchView = (SearchView) findViewById(R.id.searchview);
        searchView.setOnQueryTextListener(this);

        final ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(mAdapter = new ResultsAdapter(this));
        list.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        final Address item = (Address) parent.getItemAtPosition(position);
        if (item != null) {
            new AlertDialog.Builder(this).setMessage(item.toString()).setCancelable(true)
                    .setNeutralButton(android.R.string.cancel,
                            (DialogInterface.OnClickListener) null).show();
        }
    }

    @Override
    public boolean onQueryTextSubmit(final String s) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(final String s) {
        final String arg = s.trim();
        if (mAdapter.isEmpty() && arg.length() >= MIN_REQUEST_LENGTH) {
            setAnimatorChild(ANIMATOR_CHILD_PROGRESS);
        }
        mHandler.query(arg);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        cancelLoadTask();
    }

    private void setAnimatorChild(final int child) {
        if (mViewAnimator != null && mViewAnimator.getDisplayedChild() != child) {
            mViewAnimator.setDisplayedChild(child);
        }
    }

    private void cancelLoadTask() {
        if (mLoadTask != null && mLoadTask.getStatus() != AsyncTask.Status.FINISHED) {
            mLoadTask.cancel(true);
        }
    }

    void runQuery(@NonNull final String userInput) {
        if (userInput.length() < MIN_REQUEST_LENGTH) {
            updateData(null);
        } else {
            if (mLoadTask != null && mLoadTask.getStatus() != AsyncTask.Status.FINISHED) {
                mLoadTask.cancel(true);
            }
            mLoadTask = new LoadTask(userInput);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                mLoadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                mLoadTask.execute();
            }
        }
    }

    void updateData(@Nullable final List<Address> data) {
        mAdapter.updateData(data);
        setAnimatorChild(data == null || data.isEmpty() ? ANIMATOR_CHILD_CONTENT_EMPTY
                : ANIMATOR_CHILD_CONTENT);
    }

    private final class LoadTask extends AsyncTask<Void, Void, Object> {

        private final Geocoder mGeocoder;

        private final String mQuery;

        private LoadTask(final String query) {
            mGeocoder = new Geocoder(DemoActivity.this, Locale.getDefault());
            mQuery = query;
        }

        @Override
        protected Object doInBackground(final Void... params) {
            try {
                return mGeocoder.getFromLocationName(mQuery, 20, true);
            } catch (IOException | Geocoder.LimitExceededException e) {
                return e;
            }
        }

        @Override
        protected void onPostExecute(final Object result) {
            if (result instanceof Exception) {
                Toast.makeText(DemoActivity.this, result.toString(), Toast.LENGTH_LONG).show();
                updateData(null);
                return;
            }
            //noinspection unchecked
            updateData((List<Address>) result);
        }
    }

    private static final class DemoActivityHandler extends Handler {

        private static final long REQUEST_DELAY = 1000L;

        private static final int MSG_QUERY = 1;

        @NonNull
        private final WeakReference<DemoActivity> mFragmentRef;

        DemoActivityHandler(@NonNull final Looper looper,
                @NonNull final DemoActivity activity) {
            super(looper);
            mFragmentRef = new WeakReference<>(activity);
        }

        void query(@NonNull final String userInput) {
            removeMessages(MSG_QUERY);
            sendMessageDelayed(obtainMessage(MSG_QUERY, userInput), REQUEST_DELAY);
        }

        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case MSG_QUERY:
                    final DemoActivity f = mFragmentRef.get();
                    if (f != null) {
                        f.runQuery((String) msg.obj);
                    }
                    break;

                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    private static final class ResultsAdapter extends BaseAdapter2<Address> {

        private ResultsAdapter(@NonNull final Context context) {
            super(context);
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater()
                        .inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            final TextView textView = (TextView) convertView;
            textView.setText(getItem(position).getFormattedAddress());
            return convertView;
        }
    }
}
