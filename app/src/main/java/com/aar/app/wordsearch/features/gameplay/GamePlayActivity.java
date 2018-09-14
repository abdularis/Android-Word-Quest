package com.aar.app.wordsearch.features.gameplay;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aar.app.wordsearch.R;
import com.aar.app.wordsearch.features.SoundPlayer;
import com.aar.app.wordsearch.features.ViewModelFactory;
import com.aar.app.wordsearch.WordSearchApp;
import com.aar.app.wordsearch.commons.DurationFormatter;
import com.aar.app.wordsearch.commons.Util;
import com.aar.app.wordsearch.model.GameData;
import com.aar.app.wordsearch.custom.LetterBoard;
import com.aar.app.wordsearch.custom.StreakView;
import com.aar.app.wordsearch.features.gameover.GameOverActivity;
import com.aar.app.wordsearch.features.FullscreenActivity;
import com.aar.app.wordsearch.model.Difficulty;
import com.aar.app.wordsearch.model.GameMode;
import com.aar.app.wordsearch.model.UsedWord;
import com.google.android.flexbox.FlexboxLayout;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindColor;
import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;

public class GamePlayActivity extends FullscreenActivity {

    public static final String EXTRA_GAME_DIFFICULTY = "game_max_duration";
    public static final String EXTRA_GAME_DATA_ID = "game_data_id";
    public static final String EXTRA_GAME_MODE = "game_mode";
    public static final String EXTRA_GAME_THEME_ID = "game_theme_id";
    public static final String EXTRA_ROW_COUNT = "row_count";
    public static final String EXTRA_COL_COUNT = "col_count";

    private static final StreakLineMapper STREAK_LINE_MAPPER = new StreakLineMapper();

    @Inject
    SoundPlayer mSoundPlayer;

    @Inject ViewModelFactory mViewModelFactory;
    private GamePlayViewModel mViewModel;

    @BindView(R.id.progressDuration) ProgressBar mDurationProgress;
    @BindView(R.id.layoutComplete) View mLayoutComplete;
    @BindView(R.id.textComplete) TextView mTextComplete;
    @BindView(R.id.textPopup) TextView mTextPopup;
    @BindView(R.id.text_duration) TextView mTextDuration;
    @BindView(R.id.letter_board) LetterBoard mLetterBoard;
    @BindView(R.id.flexbox_layout) FlexboxLayout mFlexLayout;

    @BindView(R.id.text_sel_layout) View mTextSelLayout;
    @BindView(R.id.text_selection) TextView mTextSelection;

    @BindView(R.id.answered_text_count) TextView mAnsweredTextCount;
    @BindView(R.id.words_count) TextView mWordsCount;

    @BindView(R.id.finished_text) TextView mFinishedText;

    @BindView(R.id.loading) View mLoading;
    @BindView(R.id.loadingText) TextView mLoadingText;
    @BindView(R.id.content_layout) View mContentLayout;

    @BindColor(R.color.gray) int mGrayColor;
    @BindString(R.string.hidden_mask) String mHiddenMaskString;

    private ArrayLetterGridDataAdapter mLetterAdapter;
    private Animation mPopupTextAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_play);

        ButterKnife.bind(this);
        ((WordSearchApp) getApplication()).getAppComponent().inject(this);

        mLetterBoard.getStreakView().setEnableOverrideStreakLineColor(getPreferences().grayscale());
        mLetterBoard.getStreakView().setOverrideStreakLineColor(mGrayColor);
        mLetterBoard.setOnLetterSelectionListener(new LetterBoard.OnLetterSelectionListener() {
            @Override
            public void onSelectionBegin(StreakView.StreakLine streakLine, String str) {
                streakLine.setColor(Util.getRandomColorWithAlpha(170));
                mTextSelLayout.setVisibility(View.VISIBLE);
                mTextSelection.setText(str);
            }

            @Override
            public void onSelectionDrag(StreakView.StreakLine streakLine, String str) {
                if (str.isEmpty()) {
                    mTextSelection.setText("...");
                } else {
                    mTextSelection.setText(str);
                }
            }

            @Override
            public void onSelectionEnd(StreakView.StreakLine streakLine, String str) {
                mViewModel.answerWord(str, STREAK_LINE_MAPPER.revMap(streakLine), getPreferences().reverseMatching());
                mTextSelLayout.setVisibility(View.GONE);
                mTextSelection.setText(str);
            }
        });

        mViewModel = ViewModelProviders.of(this, mViewModelFactory).get(GamePlayViewModel.class);
        mViewModel.getOnTimer().observe(this, this::showDuration);
        mViewModel.getOnCountDown().observe(this, this::showCountDown);
        mViewModel.getOnGameState().observe(this, this::onGameStateChanged);
        mViewModel.getOnAnswerResult().observe(this, this::onAnswerResult);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey(EXTRA_GAME_DATA_ID)) {
                int gid = extras.getInt(EXTRA_GAME_DATA_ID);
                mViewModel.loadGameRound(gid);
            } else {
                GameMode gameMode = (GameMode) extras.get(EXTRA_GAME_MODE);
                Difficulty difficulty = (Difficulty) extras.get(EXTRA_GAME_DIFFICULTY);
                int gameThemeId = extras.getInt(EXTRA_GAME_THEME_ID);
                int rowCount = extras.getInt(EXTRA_ROW_COUNT);
                int colCount = extras.getInt(EXTRA_COL_COUNT);
                mViewModel.generateNewGameRound(rowCount, colCount, gameThemeId, gameMode, difficulty);
            }
        }

        if (!getPreferences().showGridLine()) {
            mLetterBoard.getGridLineBackground().setVisibility(View.INVISIBLE);
        } else {
            mLetterBoard.getGridLineBackground().setVisibility(View.VISIBLE);
        }

        mLetterBoard.getStreakView().setSnapToGrid(getPreferences().getSnapToGrid());
        mFinishedText.setVisibility(View.GONE);
        mPopupTextAnimation = AnimationUtils.loadAnimation(this, R.anim.popup_text);
        mPopupTextAnimation.setDuration(1000);
        mPopupTextAnimation.setInterpolator(new DecelerateInterpolator());
        mPopupTextAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationRepeat(Animation animation) { }

            @Override
            public void onAnimationStart(Animation animation) { }

            @Override
            public void onAnimationEnd(Animation animation) {
                mTextPopup.setVisibility(View.GONE);
                mTextPopup.setText("");
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mViewModel.resumeGame();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mViewModel.pauseGame();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mViewModel.stopGame();
    }

    private void onAnswerResult(GamePlayViewModel.AnswerResult answerResult) {
        if (answerResult.correct) {
            View item = findUsedWordViewItemByUsedWordId(answerResult.usedWord.getId());
            if (item != null) {
                UsedWord uw = answerResult.usedWord;

                if (getPreferences().grayscale()) {
                    uw.getAnswerLine().color = mGrayColor;
                }

                TextView str = item.findViewById(R.id.textStr);

                item.getBackground().setColorFilter(uw.getAnswerLine().color, PorterDuff.Mode.MULTIPLY);
                str.setText(uw.getString());
                str.setTextColor(Color.WHITE);
                str.setPaintFlags(str.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

                item.startAnimation(AnimationUtils.loadAnimation(this, R.anim.zoom_in_out));

                mTextPopup.setVisibility(View.VISIBLE);
                mTextPopup.setText(uw.getString());
                mTextPopup.startAnimation(mPopupTextAnimation);
            }

            showAnsweredWordsCount(answerResult.totalAnsweredWord);
            mSoundPlayer.play(SoundPlayer.Sound.Correct);
        }
        else {
            mLetterBoard.popStreakLine();

            mSoundPlayer.play(SoundPlayer.Sound.Wrong);
        }
    }

    private void onGameStateChanged(GamePlayViewModel.GameState gameState) {
        showLoading(false, null);
        if (gameState instanceof GamePlayViewModel.Generating) {
            GamePlayViewModel.Generating state = (GamePlayViewModel.Generating) gameState;
            String text = "Generating " + state.rowCount + "x" + state.colCount + " grid";
            showLoading(true, text);
        } else if (gameState instanceof GamePlayViewModel.Loading) {
            showLoading(true, "Loading game data");
        } else if (gameState instanceof GamePlayViewModel.Finished) {
            showFinishGame((GamePlayViewModel.Finished) gameState);
        } else if (gameState instanceof GamePlayViewModel.Paused) {

        } else if (gameState instanceof GamePlayViewModel.Playing) {
            onGameRoundLoaded(((GamePlayViewModel.Playing) gameState).mGameData);
        }
    }

    private void onGameRoundLoaded(GameData gameData) {
        if (gameData.isFinished()) {
            mLetterBoard.getStreakView().setInteractive(false);
            mFinishedText.setVisibility(View.VISIBLE);
            mLayoutComplete.setVisibility(View.VISIBLE);
            mTextComplete.setText(R.string.lbl_complete);
        } else if (gameData.isGameOver()) {
            mLetterBoard.getStreakView().setInteractive(false);
            mLayoutComplete.setVisibility(View.VISIBLE);
            mTextComplete.setText(R.string.lbl_game_over);
        }

        showLetterGrid(gameData.getGrid().getArray());
        showDuration(gameData.getDuration());
        showUsedWords(gameData.getUsedWords(), gameData);
        showWordsCount(gameData.getUsedWords().size());
        showAnsweredWordsCount(gameData.getAnsweredWordsCount());
        doneLoadingContent();

        if (gameData.getGameMode() == GameMode.CountDown) {
            mDurationProgress.setVisibility(View.VISIBLE);
            mDurationProgress.setMax(gameData.getMaxDuration());
            mDurationProgress.setProgress(gameData.getRemainingDuration());
        } else {
            mDurationProgress.setVisibility(View.GONE);
        }
    }

    private void tryScale() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        int boardWidth = mLetterBoard.getWidth();
        int screenWidth = metrics.widthPixels;

        if (getPreferences().autoScaleGrid() || boardWidth > screenWidth) {
            float scale = (float)screenWidth / (float)boardWidth;
            mLetterBoard.scale(scale, scale);
        }
    }

    private void doneLoadingContent() {
        // call tryScale() on the next render frame
        new Handler().postDelayed(this::tryScale, 100);
    }

    private void showLoading(boolean enable, String text) {
        if (enable) {
            mLoading.setVisibility(View.VISIBLE);
            mLoadingText.setVisibility(View.VISIBLE);
            mContentLayout.setVisibility(View.GONE);
            mLoadingText.setText(text);
        } else {
            mLoading.setVisibility(View.GONE);
            mLoadingText.setVisibility(View.GONE);
            if (mContentLayout.getVisibility() == View.GONE) {
                mContentLayout.setVisibility(View.VISIBLE);
                mContentLayout.setScaleY(.5f);
                mContentLayout.setAlpha(0f);

                mContentLayout.animate()
                        .scaleY(1f)
                        .setDuration(300)
                        .start();
                mContentLayout.animate()
                        .alpha(1f)
                        .setDuration(600)
                        .start();
            }
        }
    }

    private void showLetterGrid(char[][] grid) {
        if (mLetterAdapter == null) {
            mLetterAdapter = new ArrayLetterGridDataAdapter(grid);
            mLetterBoard.setDataAdapter(mLetterAdapter);
        }
        else {
            mLetterAdapter.setGrid(grid);
        }
    }

    private void showDuration(int duration) {
        mTextDuration.setText(DurationFormatter.fromInteger(duration));
    }

    private void showCountDown(int countDown) {
        mDurationProgress.setProgress(countDown);
    }

    private void showUsedWords(List<UsedWord> usedWords, GameData gameData) {
        mFlexLayout.removeAllViews();
        for (UsedWord uw : usedWords) {
            mFlexLayout.addView( createUsedWordTextView(uw, gameData) );
        }
    }

    private void showAnsweredWordsCount(int count) {
        mAnsweredTextCount.setText(String.valueOf(count));
    }

    private void showWordsCount(int count) {
        mWordsCount.setText(String.valueOf(count));
    }

    private void showFinishGame(GamePlayViewModel.Finished state) {
        mLetterBoard.getStreakView().setInteractive(false);

        final Animation anim = AnimationUtils.loadAnimation(this, R.anim.game_complete);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.setDuration(500);
        anim.setStartOffset(1000);

        if (state.win) {
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}
                @Override
                public void onAnimationRepeat(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    new Handler().postDelayed(() -> {
                        Intent intent = new Intent(GamePlayActivity.this, GameOverActivity.class);
                        intent.putExtra(GameOverActivity.EXTRA_GAME_ROUND_ID, state.mGameData.getId());
                        startActivity(intent);
                        finish();
                    }, 800);
                }
            });
            mTextComplete.setText(R.string.lbl_complete);
        } else {
            mTextComplete.setText(R.string.lbl_game_over);
        }

        mLayoutComplete.setVisibility(View.VISIBLE);
        mLayoutComplete.startAnimation(anim);
    }

    //
    private View createUsedWordTextView(UsedWord uw, GameData gameData) {
        View v = getLayoutInflater().inflate(R.layout.item_word, mFlexLayout, false);
        TextView str = v.findViewById(R.id.textStr);
        if (uw.isAnswered()) {
            if (getPreferences().grayscale()) {
                uw.getAnswerLine().color = mGrayColor;
            }
            v.getBackground().setColorFilter(uw.getAnswerLine().color, PorterDuff.Mode.MULTIPLY);
            str.setText(uw.getString());
            str.setTextColor(Color.WHITE);
            str.setPaintFlags(str.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

            mLetterBoard.addStreakLine(STREAK_LINE_MAPPER.map(uw.getAnswerLine()));
        }
        else {
            if (gameData.getGameMode() == GameMode.Hidden) {
                str.setText(getHiddenMask(uw.getString()));
            } else {
                str.setText(uw.getString());
            }
        }

        v.setTag(uw.getId());
        return v;
    }

    private String getHiddenMask(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (int i = 0; i < string.length(); i++)
            sb.append(mHiddenMaskString);
        return sb.toString();
    }

    private View findUsedWordViewItemByUsedWordId(int usedWordId) {
        for (int i = 0; i < mFlexLayout.getChildCount(); i++) {
            View v = mFlexLayout.getChildAt(i);
            int id = (int) v.getTag();
            if (id == usedWordId) {
                return v;
            }
        }

        return null;
    }
}
