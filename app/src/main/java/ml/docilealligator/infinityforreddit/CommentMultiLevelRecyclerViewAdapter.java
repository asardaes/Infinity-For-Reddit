package ml.docilealligator.infinityforreddit;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ColorFilter;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.multilevelview.MultiLevelAdapter;
import com.multilevelview.MultiLevelRecyclerView;
import com.multilevelview.models.RecyclerViewItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Retrofit;
import ru.noties.markwon.SpannableConfiguration;
import ru.noties.markwon.view.MarkwonView;

class CommentMultiLevelRecyclerViewAdapter extends MultiLevelAdapter {
    private Activity mActivity;
    private Retrofit mRetrofit;
    private Retrofit mOauthRetrofit;
    private SharedPreferences mSharedPreferences;
    private ArrayList<CommentData> mCommentData;
    private MultiLevelRecyclerView mMultiLevelRecyclerView;
    private String subredditNamePrefixed;
    private String article;
    private Locale locale;

    CommentMultiLevelRecyclerViewAdapter(Activity activity, Retrofit retrofit, Retrofit oauthRetrofit,
                                         SharedPreferences sharedPreferences, ArrayList<CommentData> commentData,
                                         MultiLevelRecyclerView multiLevelRecyclerView,
                                         String subredditNamePrefixed, String article, Locale locale) {
        super(commentData);
        mActivity = activity;
        mRetrofit = retrofit;
        mOauthRetrofit = oauthRetrofit;
        mSharedPreferences = sharedPreferences;
        mCommentData = commentData;
        mMultiLevelRecyclerView = multiLevelRecyclerView;
        this.subredditNamePrefixed = subredditNamePrefixed;
        this.article = article;
        this.locale = locale;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new CommentViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false));
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        final CommentData commentItem = mCommentData.get(position);

        String authorPrefixed = "u/" + commentItem.getAuthor();
        ((CommentViewHolder) holder).authorTextView.setText(authorPrefixed);
        ((CommentViewHolder) holder).authorTextView.setOnClickListener(view -> {
            Intent intent = new Intent(mActivity, ViewUserDetailActivity.class);
            intent.putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, commentItem.getAuthor());
            mActivity.startActivity(intent);
        });

        ((CommentViewHolder) holder).commentTimeTextView.setText(commentItem.getCommentTime());
        SpannableConfiguration spannableConfiguration = SpannableConfiguration.builder(mActivity).linkResolver((view, link) -> {
            if (link.startsWith("/u/") || link.startsWith("u/")) {
                Intent intent = new Intent(mActivity, ViewUserDetailActivity.class);
                intent.putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, link.substring(3));
                mActivity.startActivity(intent);
            } else if (link.startsWith("/r/") || link.startsWith("r/")) {
                Intent intent = new Intent(mActivity, ViewSubredditDetailActivity.class);
                intent.putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, link.substring(3));
                mActivity.startActivity(intent);
            } else {
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                // add share action to menu list
                builder.addDefaultShareMenuItem();
                builder.setToolbarColor(mActivity.getResources().getColor(R.color.colorPrimary));
                CustomTabsIntent customTabsIntent = builder.build();
                customTabsIntent.launchUrl(mActivity, Uri.parse(link));
            }
        }).build();

        ((CommentViewHolder) holder).commentMarkdownView.setMarkdown(spannableConfiguration, commentItem.getCommentContent());
        ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore()));

        ((CommentViewHolder) holder).verticalBlock.getLayoutParams().width = commentItem.getDepth() * 16;
        if (commentItem.hasReply()) {
            setExpandButton(((CommentViewHolder) holder).expandButton, commentItem.isExpanded());
        }

        ((CommentViewHolder) holder).shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                String extraText = commentItem.getPermalink();
                intent.putExtra(Intent.EXTRA_TEXT, extraText);
                mActivity.startActivity(Intent.createChooser(intent, "Share"));
            }
        });

        ((CommentViewHolder) holder).expandButton.setOnClickListener(view -> {
            if (commentItem.hasChildren() && commentItem.getChildren().size() > 0) {
                mMultiLevelRecyclerView.toggleItemsGroup(holder.getAdapterPosition());
                setExpandButton(((CommentViewHolder) holder).expandButton, commentItem.isExpanded());
            } else {
                ((CommentViewHolder) holder).loadMoreCommentsProgressBar.setVisibility(View.VISIBLE);
                FetchComment.fetchAllComment(mRetrofit, subredditNamePrefixed, article, commentItem.getId(),
                        locale, false, commentItem.getDepth(), new FetchComment.FetchAllCommentListener() {
                            @Override
                            public void onFetchAllCommentSuccess(List<?> commentData) {
                                commentItem.addChildren((List<RecyclerViewItem>) commentData);
                                ((CommentViewHolder) holder).loadMoreCommentsProgressBar
                                        .setVisibility(View.GONE);
                                mMultiLevelRecyclerView.toggleItemsGroup(holder.getAdapterPosition());
                                ((CommentViewHolder) holder).expandButton
                                        .setImageResource(R.drawable.ic_expand_less_black_20dp);
                            }

                            @Override
                            public void onFetchAllCommentFailed() {
                                ((CommentViewHolder) holder).loadMoreCommentsProgressBar
                                        .setVisibility(View.GONE);
                            }
                        });
            }
        });

        ((CommentViewHolder) holder).replyButton.setOnClickListener(view -> {
            Intent intent = new Intent(mActivity, CommentActivity.class);
            intent.putExtra(CommentActivity.EXTRA_PARENT_DEPTH_KEY, commentItem.getDepth() + 1);
            intent.putExtra(CommentActivity.EXTRA_COMMENT_PARENT_TEXT_KEY, commentItem.getCommentContent());
            intent.putExtra(CommentActivity.EXTRA_PARENT_FULLNAME_KEY, commentItem.getFullName());
            intent.putExtra(CommentActivity.EXTRA_IS_REPLYING_KEY, true);
            mActivity.startActivityForResult(intent, CommentActivity.WRITE_COMMENT_REQUEST_CODE);
        });

        switch (commentItem.getVoteType()) {
            case 1:
                ((CommentViewHolder) holder).upvoteButton
                        .setColorFilter(ContextCompat.getColor(mActivity, R.color.colorPrimary), android.graphics.PorterDuff.Mode.SRC_IN);
                break;
            case 2:
                ((CommentViewHolder) holder).downvoteButton
                        .setColorFilter(ContextCompat.getColor(mActivity, R.color.minusButtonColor), android.graphics.PorterDuff.Mode.SRC_IN);
                break;
        }

        ((CommentViewHolder) holder).upvoteButton.setOnClickListener(view -> {
            ColorFilter previousUpvoteButtonColorFilter = ((CommentViewHolder) holder).upvoteButton.getColorFilter();
            ColorFilter previousDownvoteButtonColorFilter = ((CommentViewHolder) holder).downvoteButton.getColorFilter();
            int previousVoteType = commentItem.getVoteType();
            String newVoteType;

            ((CommentViewHolder) holder).downvoteButton.clearColorFilter();

            if(previousUpvoteButtonColorFilter == null) {
                //Not upvoted before
                commentItem.setVoteType(1);
                newVoteType = RedditUtils.DIR_UPVOTE;
                ((CommentViewHolder) holder).upvoteButton
                        .setColorFilter(ContextCompat.getColor(mActivity, R.color.backgroundColorPrimaryDark), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                //Upvoted before
                commentItem.setVoteType(0);
                newVoteType = RedditUtils.DIR_UNVOTE;
                ((CommentViewHolder) holder).upvoteButton.clearColorFilter();
            }

            ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() + commentItem.getVoteType()));

            VoteThing.voteThing(mOauthRetrofit, mSharedPreferences, new VoteThing.VoteThingListener() {
                @Override
                public void onVoteThingSuccess(int position1) {
                    if(newVoteType.equals(RedditUtils.DIR_UPVOTE)) {
                        commentItem.setVoteType(1);
                        ((CommentViewHolder) holder).upvoteButton
                                .setColorFilter(ContextCompat.getColor(mActivity, R.color.backgroundColorPrimaryDark), android.graphics.PorterDuff.Mode.SRC_IN);
                    } else {
                        commentItem.setVoteType(0);
                        ((CommentViewHolder) holder).upvoteButton.clearColorFilter();
                    }

                    ((CommentViewHolder) holder).downvoteButton.clearColorFilter();
                    ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() + commentItem.getVoteType()));
                }

                @Override
                public void onVoteThingFail(int position1) {
                    Toast.makeText(mActivity, R.string.vote_failed, Toast.LENGTH_SHORT).show();
                    commentItem.setVoteType(previousVoteType);
                    ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() + previousVoteType));
                    ((CommentViewHolder) holder).upvoteButton.setColorFilter(previousUpvoteButtonColorFilter);
                    ((CommentViewHolder) holder).downvoteButton.setColorFilter(previousDownvoteButtonColorFilter);
                }
            }, commentItem.getFullName(), newVoteType, holder.getAdapterPosition());
        });

        ((CommentViewHolder) holder).downvoteButton.setOnClickListener(view -> {
            ColorFilter previousUpvoteButtonColorFilter = ((CommentViewHolder) holder).upvoteButton.getColorFilter();
            ColorFilter previousDownvoteButtonColorFilter = ((CommentViewHolder) holder).downvoteButton.getColorFilter();
            int previousVoteType = commentItem.getVoteType();
            String newVoteType;

            ((CommentViewHolder) holder).upvoteButton.clearColorFilter();

            if(previousDownvoteButtonColorFilter == null) {
                //Not downvoted before
                commentItem.setVoteType(-1);
                newVoteType = RedditUtils.DIR_DOWNVOTE;
                ((CommentViewHolder) holder).downvoteButton
                        .setColorFilter(ContextCompat.getColor(mActivity, R.color.colorAccent), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                //Downvoted before
                commentItem.setVoteType(0);
                newVoteType = RedditUtils.DIR_UNVOTE;
                ((CommentViewHolder) holder).downvoteButton.clearColorFilter();
            }

            ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() + commentItem.getVoteType()));

            VoteThing.voteThing(mOauthRetrofit, mSharedPreferences, new VoteThing.VoteThingListener() {
                @Override
                public void onVoteThingSuccess(int position1) {
                    if(newVoteType.equals(RedditUtils.DIR_DOWNVOTE)) {
                        commentItem.setVoteType(-1);
                        ((CommentViewHolder) holder).downvoteButton
                                .setColorFilter(ContextCompat.getColor(mActivity, R.color.colorAccent), android.graphics.PorterDuff.Mode.SRC_IN);
                    } else {
                        commentItem.setVoteType(0);
                        ((CommentViewHolder) holder).downvoteButton.clearColorFilter();
                    }

                    ((CommentViewHolder) holder).upvoteButton.clearColorFilter();
                    ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() + commentItem.getVoteType()));
                }

                @Override
                public void onVoteThingFail(int position1) {
                    Toast.makeText(mActivity, R.string.vote_failed, Toast.LENGTH_SHORT).show();
                    commentItem.setVoteType(previousVoteType);
                    ((CommentViewHolder) holder).scoreTextView.setText(Integer.toString(commentItem.getScore() + previousVoteType));
                    ((CommentViewHolder) holder).upvoteButton.setColorFilter(previousUpvoteButtonColorFilter);
                    ((CommentViewHolder) holder).downvoteButton.setColorFilter(previousDownvoteButtonColorFilter);
                }
            }, commentItem.getFullName(), newVoteType, holder.getAdapterPosition());
        });
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof CommentViewHolder) {
            ((CommentViewHolder) holder).expandButton.setVisibility(View.GONE);
            ((CommentViewHolder) holder).loadMoreCommentsProgressBar.setVisibility(View.GONE);
        }
    }

    void addComments(ArrayList<CommentData> comments) {
        int sizeBefore = mCommentData.size();
        mCommentData.addAll(comments);
        notifyItemRangeInserted(sizeBefore, mCommentData.size() - sizeBefore);
    }

    void addComment(CommentData comment) {
        mCommentData.add(0, comment);
        notifyItemInserted(0);
    }

    void addChildComment(CommentData comment, int parentPosition) {
        List<RecyclerViewItem> childComments = mCommentData.get(parentPosition).getChildren();
        if(childComments == null) {
            childComments = new ArrayList<>();
        }
        childComments.add(0, comment);
        mCommentData.get(parentPosition).addChildren(childComments);
        mCommentData.get(parentPosition).setHasReply(true);
        notifyItemChanged(parentPosition);
        mMultiLevelRecyclerView.toggleItemsGroup(parentPosition);
    }

    private void setExpandButton(ImageView expandButton, boolean isExpanded) {
        // set the icon based on the current state
        expandButton.setVisibility(View.VISIBLE);
        expandButton.setImageResource(isExpanded ? R.drawable.ic_expand_less_black_20dp : R.drawable.ic_expand_more_black_20dp);
    }

    class CommentViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.author_text_view_item_post_comment) TextView authorTextView;
        @BindView(R.id.comment_time_text_view_item_post_comment) TextView commentTimeTextView;
        @BindView(R.id.comment_markdown_view_item_post_comment) MarkwonView commentMarkdownView;
        @BindView(R.id.plus_button_item_post_comment) ImageView upvoteButton;
        @BindView(R.id.score_text_view_item_post_comment) TextView scoreTextView;
        @BindView(R.id.minus_button_item_post_comment) ImageView downvoteButton;
        @BindView(R.id.load_more_comments_progress_bar) ProgressBar loadMoreCommentsProgressBar;
        @BindView(R.id.expand_button_item_post_comment) ImageView expandButton;
        @BindView(R.id.share_button_item_post_comment) ImageView shareButton;
        @BindView(R.id.reply_button_item_post_comment) ImageView replyButton;
        @BindView(R.id.vertical_block_item_post_comment) View verticalBlock;

        CommentViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
