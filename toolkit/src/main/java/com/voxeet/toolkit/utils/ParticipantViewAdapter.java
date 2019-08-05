package com.voxeet.toolkit.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.voxeet.android.media.MediaStream;
import com.voxeet.sdk.core.VoxeetSdk;
import com.voxeet.sdk.models.ConferenceUserStatus;
import com.voxeet.sdk.models.abs.ConferenceUser;
import com.voxeet.sdk.views.VideoView;
import com.voxeet.toolkit.R;
import com.voxeet.toolkit.views.internal.rounded.RoundedImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParticipantViewAdapter extends RecyclerView.Adapter<ParticipantViewAdapter.ViewHolder> {

    private final String TAG = ParticipantViewAdapter.class.getSimpleName();

    private boolean namesEnabled = true;

    private List<ConferenceUser> users;

    private Context context;

    private int avatarSize;

    private int lastPosition = -1;

    private String selectedUserId = null;

    private IParticipantViewListener listener;

    private int selectedUserColor;

    private String mRequestUserIdChanged;

    private ParticipantViewAdapter() {

    }

    /**
     * Instantiates a new Participant view adapter.
     *
     * @param context the context
     */
    public ParticipantViewAdapter(Context context) {
        this();
        this.selectedUserColor = context.getResources().getColor(R.color.blue);

        this.context = context;

        this.users = new ArrayList<>();

        this.namesEnabled = true;

        this.avatarSize = context.getResources().getDimensionPixelSize(R.dimen.meeting_list_avatar_double);
    }

    /**
     * Removes the conference user if being part of the conference.
     *
     * @param conferenceUser the conference user
     */
    public void removeUser(ConferenceUser conferenceUser) {
        if (users.contains(conferenceUser))
            users.remove(conferenceUser);

        filter();
        sort();
    }

    public void updateUsers() {
        filter();
        sort();

        notifyDataSetChanged();
    }

    /**
     * Adds the conference user if not already added.
     *
     * @param conferenceUser the conference user
     */
    public void addUser(ConferenceUser conferenceUser) {
        if (!users.contains(conferenceUser))
            users.add(conferenceUser);

        filter();
        sort();
    }

    private void filter() {
        List<ConferenceUser> temp_users = new ArrayList<>();

        for (ConferenceUser user : users) {
            if (!ConferenceUserStatus.LEFT.equals(user.getConferenceStatus())) temp_users.add(user);
        }

        users = temp_users;
    }

    private void sort() {
        if (null != users) {
            Collections.sort(users, new Comparator<ConferenceUser>() {
                @Override
                public int compare(ConferenceUser left, ConferenceUser right) {
                    if (null != left && ConferenceUserStatus.ON_AIR.equals(left.getConferenceStatus()))
                        return -1;
                    if (null != right && ConferenceUserStatus.ON_AIR.equals(right.getConferenceStatus()))
                        return 1;
                    return 0;
                }
            });
        }
    }

    /**
     * Sets the color when a conference user has been selected.
     *
     * @param color the color
     */
    public void setSelectedUserColor(int color) {
        selectedUserColor = color;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_participant_view_cell, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final ConferenceUser user = getItem(position);

        Log.d(TAG, "onBindViewHolder: " + position + " " + user.getConferenceStatus());

        boolean on_air = ConferenceUserStatus.ON_AIR.equals(user.getConferenceStatus());

        if (null != user.getUserInfo()) {
            holder.name.setText(user.getUserInfo().getName());
        } else if (null != user.getProfile()) {
            holder.name.setText(user.getProfile().getNickName());
        }
        holder.name.setVisibility(namesEnabled ? View.VISIBLE : View.GONE);

        loadViaPicasso(user, holder.avatar);

        Log.d(TAG, "onBindViewHolder: ");
        if (on_air) {
            holder.itemView.setAlpha(1f);
            holder.avatar.setAlpha(1.0f);
        } else {
            holder.itemView.setAlpha(0.5f);
            holder.avatar.setAlpha(0.4f);
        }

        if (equalsToUser(selectedUserId, user)) {
            holder.name.setTypeface(Typeface.DEFAULT_BOLD);
            holder.name.setTextColor(context.getResources().getColor(R.color.white));

            holder.overlay.setVisibility(View.VISIBLE);
            holder.overlay.setBackgroundColor(selectedUserColor);
        } else {
            holder.name.setTypeface(Typeface.DEFAULT);
            holder.name.setTextColor(context.getResources().getColor(R.color.grey999));

            holder.overlay.setVisibility(View.GONE);
        }

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {

                if (equalsToUser(selectedUserId, user)) {
                    selectedUserId = null;

                    if (listener != null)
                        listener.onParticipantUnselected(user);
                    notifyDataSetChanged();
                }
                return true;
            }
        });

        if (null != mRequestUserIdChanged && mRequestUserIdChanged.equals(user.getUserId())) {
            String userId = mRequestUserIdChanged;
            VideoView.MediaStreamType type = null;
            MediaStream stream = null;
            if (VideoView.MediaStreamType.NONE.equals(type)) {
                if (hasScreenShareMediaStream(userId)) {
                    type = getPrevious(userId, VideoView.MediaStreamType.SCREEN_SHARE);
                } else if (hasCameraMediaStream(userId)) {
                    type = getPrevious(userId, VideoView.MediaStreamType.VIDEO);
                }
            } else if (hasScreenShareMediaStream(userId)) {
                stream = getScreenShareMediaStream(userId);
                type = getPrevious(userId, VideoView.MediaStreamType.SCREEN_SHARE);
            } else if (hasCameraMediaStream(userId)) {
                stream = getCameraMediaStream(userId);
                type = getPrevious(userId, VideoView.MediaStreamType.VIDEO);
            }

            if(!on_air) type = VideoView.MediaStreamType.NONE;
            Log.d(TAG, "onBindViewHolder: load previous " + type);

            loadStreamOnto(mRequestUserIdChanged, type, holder);

            if (listener != null)
                listener.onParticipantSelected(user, stream);

            //prevent any modification until next event
            mRequestUserIdChanged = null;
        } else {
            String userId = user.getUserId();
            VideoView.MediaStreamType type = getCurrentType(holder.videoView, userId);
            //if (VideoView.MediaStreamType.NONE.equals(type)) {
            if (hasScreenShareMediaStream(userId)) {
                type = VideoView.MediaStreamType.SCREEN_SHARE;
            } else if (hasCameraMediaStream(userId)) {
                type = VideoView.MediaStreamType.VIDEO;
            }

            if(!on_air) type = VideoView.MediaStreamType.NONE;
            Log.d(TAG, "onBindViewHolder: loading stream ? " + type);
            loadStreamOnto(userId, type, holder);
            //}
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!on_air) {
                    Log.d(TAG, "onClick: click on an invalid user, we can't select him");
                    return;
                }

                String userId = user.getUserId();
                //toggle media screen call next stream
                VideoView.MediaStreamType current_type = holder.videoView.getCurrentMediaStreamType();

                VideoView.MediaStreamType next = getNext(user.getUserId(), current_type);

                if(!on_air) next = VideoView.MediaStreamType.NONE;
                Log.d(TAG, "onClick: loading stream type " + next);
                loadStreamOnto(userId, next, holder);

                //now get the one for the main view
                next = getNext(user.getUserId(), next);

                MediaStream stream = null;

                switch (next) {
                    case SCREEN_SHARE:
                        stream = getScreenShareMediaStream(userId);
                        break;
                    case VIDEO:
                        stream = getCameraMediaStream(userId);
                        break;
                }
                Log.d(TAG, "onClick: sending stream type to listener " + next);

                if (null != user.getUserId()) {
                    Log.d(TAG, "onClick: selecting the user " + user.getUserId());
                    if (null == selectedUserId || !equalsToUser(selectedUserId, user)) {
                        selectedUserId = user.getUserId();

                        if (listener != null)
                            listener.onParticipantSelected(user, stream);
                    } else {
                        selectedUserId = null; //deselecting

                        if (listener != null)
                            listener.onParticipantUnselected(user);
                    }

                    notifyDataSetChanged();
                }
            }
        });

        setAnimation(holder.itemView, position);
    }

    private boolean equalsToUser(String selectedUserId, ConferenceUser user) {
        boolean areEquals = null != selectedUserId && null != user && selectedUserId.equals(user.getUserId());
        Log.d(TAG, "equalsToUser: are equals ? " + selectedUserId + " " + user + " := " + areEquals);
        return areEquals;
    }

    private void loadStreamOnto(String userId, @Nullable VideoView.MediaStreamType type, ViewHolder holder) {
        if (null == type) type = VideoView.MediaStreamType.NONE;
        holder.videoView.setAutoUnAttach(true);
        switch (type) {
            case NONE:
                holder.videoView.unAttach();
                holder.videoView.setVisibility(View.GONE);
                holder.avatar.setVisibility(View.VISIBLE);
                break;
            case SCREEN_SHARE:
                holder.videoView.attach(userId, getScreenShareMediaStream(userId));
                holder.videoView.setVisibility(View.VISIBLE);
                holder.avatar.setVisibility(View.GONE);
                break;
            case VIDEO:
                holder.videoView.attach(userId, getCameraMediaStream(userId));
                holder.videoView.setVisibility(View.VISIBLE);
                holder.avatar.setVisibility(View.GONE);
                break;
        }
    }

    private VideoView.MediaStreamType getCurrentType(VideoView videoView, String userId) {
        VideoView.MediaStreamType type = videoView.getCurrentMediaStreamType();

        switch (type) {
            case NONE:
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
                break;
            case SCREEN_SHARE:
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
                break;
            case VIDEO:
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
        }
        return VideoView.MediaStreamType.NONE;
    }

    @NonNull
    private VideoView.MediaStreamType getPrevious(String userId, VideoView.MediaStreamType type) {
        switch (type) {
            case NONE:
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                break;
            case SCREEN_SHARE:
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                break;
            case VIDEO:
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
        }
        return VideoView.MediaStreamType.NONE;
    }

    @NonNull
    private VideoView.MediaStreamType getNext(String userId, VideoView.MediaStreamType type) {
        switch (type) {
            case NONE:
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                break;
            case VIDEO:
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
                break;
            case SCREEN_SHARE:
                if (hasCameraMediaStream(userId))
                    return VideoView.MediaStreamType.VIDEO;
                if (hasScreenShareMediaStream(userId))
                    return VideoView.MediaStreamType.SCREEN_SHARE;
        }
        return VideoView.MediaStreamType.NONE;
    }

    /**
     * Animation when a new participant is joining the conference.
     *
     * @param viewToAnimate the valid view which must be animated
     * @param position      the position in the list
     */
    private void setAnimation(@NonNull View viewToAnimate, int position) {
        if (position > lastPosition) { // If the bound view wasn't previously displayed on screen, it's animated
            AlphaAnimation animation = new AlphaAnimation(0.2f, 1.0f);
            animation.setDuration(500);
            animation.setFillAfter(true);
            viewToAnimate.startAnimation(animation);

            lastPosition = position;
        }
    }

    private ConferenceUser getItem(int position) {
        return users.get(position);
    }

    /**
     * Displays user's avatar in the specified imageView.
     *
     * @param conferenceUser a valid user to bind into picasso
     * @param imageView      the landing image view
     */
    private void loadViaPicasso(@NonNull ConferenceUser conferenceUser, ImageView imageView) {
        try {
            String url = conferenceUser.getUserInfo().getAvatarUrl();
            Log.d(TAG, "loadViaPicasso: loading " + url + " for user " + conferenceUser.getUserInfo().getExternalId() + " instance := " + Picasso.get());
            if (!TextUtils.isEmpty(url)) {
                Picasso.get()
                        .load(url)
                        .noFade()
                        .resize(avatarSize, avatarSize)
                        .placeholder(R.drawable.default_avatar)
                        .error(R.color.red)
                        .into(imageView);
            } else {
                Picasso.get()
                        .load(R.drawable.default_avatar)
                        .into(imageView);
            }
        } catch (Exception e) {
            Log.e(TAG, "error " + e.getMessage());
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    /**
     * Sets participant listener.
     *
     * @param listener the listener
     */
    public void setParticipantListener(IParticipantViewListener listener) {
        this.listener = listener;
    }

    /**
     * On media stream updated. Needs to call notifyDataSetChanged to refresh the videos.
     *
     * @param mediaStreams the media streams
     */
    public void onMediaStreamUpdated(@Nullable String userId, @NonNull Map<String, MediaStream> mediaStreams) {
        //mMediaStreamMap = mediaStreams;
        if (null != userId && mediaStreams.containsKey(userId)) {
            MediaStream stream = mediaStreams.get(userId);
            if (null != stream && stream.videoTracks().size() > 0) {
                mRequestUserIdChanged = userId;
            }
        }

        notifyDataSetChanged();
    }

    public void onScreenShareMediaStreamUpdated(String userId, Map<String, MediaStream> screenSharemediaStreams) {
        //mScreenShareMediaStreams = screenSharemediaStreams;

        if (screenSharemediaStreams.containsKey(userId)) {
            MediaStream stream = screenSharemediaStreams.get(userId);
            if (null != stream && stream.isScreenShare()) {
                mRequestUserIdChanged = userId;
            }
        }

        notifyDataSetChanged();
    }

    /**
     * Clear participants.
     */
    public void clearParticipants() {
        this.users.clear();
    }

    /**
     * Sets names enabled.
     *
     * @param enabled the enabled
     */
    public void setNamesEnabled(boolean enabled) {
        namesEnabled = enabled;
    }

    public String getSelectedUserId() {
        return mRequestUserIdChanged;
    }

    /**
     * The type View holder.
     */
    class ViewHolder extends RecyclerView.ViewHolder {
        private VideoView videoView;

        private TextView name;

        private RoundedImageView avatar;

        private ImageView overlay;

        /**
         * Instantiates a new View holder.
         *
         * @param view the view
         */
        ViewHolder(@NonNull View view) {
            super(view);

            videoView = (VideoView) view.findViewById(R.id.participant_video_view);

            name = (TextView) view.findViewById(R.id.name);

            overlay = (ImageView) view.findViewById(R.id.overlay_avatar);

            avatar = (RoundedImageView) view.findViewById(R.id.avatar);
        }
    }

    @Nullable
    private MediaStream getScreenShareMediaStream(@NonNull String userId) {
        //if (null != mScreenShareMediaStreams && mScreenShareMediaStreams.containsKey(userId))
        //    return mScreenShareMediaStreams.get(userId);
        //return null;
        HashMap<String, MediaStream> streams = VoxeetSdk.conference().getMapOfScreenShareStreams();
        return streams.containsKey(userId) ? streams.get(userId) : null;
    }

    @Nullable
    private MediaStream getCameraMediaStream(@NonNull String userId) {
        //if (hasCameraMediaStream(userId)) return mMediaStreamMap.get(userId);
        //return null;
        HashMap<String, MediaStream> streams = VoxeetSdk.conference().getMapOfStreams();
        return streams.containsKey(userId) ? streams.get(userId) : null;
    }

    private boolean hasScreenShareMediaStream(@NonNull String userId) {
        //MediaStream stream = getScreenShareMediaStream(userId);
        //return null != stream;

        //We are disabling the attachment of screen share stream from this adapter for now
        //the behaviour will evolve in the future

        return false;
    }

    private boolean hasCameraMediaStream(@NonNull String userId) {
        MediaStream stream = getMediaStream(userId);
        if (null != stream) return stream.videoTracks().size() > 0;
        return false;
    }

    @Nullable
    private MediaStream getMediaStream(@NonNull String userId) {
        //if (null != mMediaStreamMap && mMediaStreamMap.containsKey(userId))
        //    return mMediaStreamMap.get(userId);
        //return null;
        HashMap<String, MediaStream> streams = VoxeetSdk.conference().getMapOfStreams();
        return streams.containsKey(userId) ? streams.get(userId) : null;
    }
}
