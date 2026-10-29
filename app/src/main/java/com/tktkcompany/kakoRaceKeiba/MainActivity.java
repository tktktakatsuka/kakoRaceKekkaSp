package com.tktkcompany.kakoRaceKeiba;

import com.tktkcompany.kakoRaceKeiba.util.Lang;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.tktkcompany.kakoRaceKeiba.ads.AdFree;
import com.tktkcompany.kakoRaceKeiba.auth.GuestAccount;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.messaging.FirebaseMessaging;
import com.tktkcompany.kakoRaceKeiba.databinding.ActivityMainBinding;
import com.tktkcompany.kakoRaceKeiba.dto.SharedViewModel;
import com.tktkcompany.kakoRaceKeiba.notification.FavoriteResultWorker;
import com.tktkcompany.kakoRaceKeiba.notification.NotificationHelper;
import com.tktkcompany.kakoRaceKeiba.ui.entries.EntriesFragment;
import com.tktkcompany.kakoRaceKeiba.ui.home.HomeFragment;
import com.tktkcompany.kakoRaceKeiba.ui.horsememo.HorseMemoDialog;
import com.tktkcompany.kakoRaceKeiba.ui.horsememo.HorseMemos;

import java.util.HashMap;

import static com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private CredentialManager credentialManager;
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    // MainActivity のフィールド
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    // 通知権限（Android 13以降）のリクエスト用
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    // 退会の本人確認のための Google ログイン中なら true（ログインできたら、つなぎ替えではなく退会を続ける）
    private boolean reauthForDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        // CredentialManager 初期化
        credentialManager = CredentialManager.create(this);

        // ActivityResultLauncher 初期化（Google Sign-In fallback 用）
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            if (account != null) {
                                String idToken = account.getIdToken();
                                firebaseAuthWithGoogle(idToken); // 既存のFirebase認証処理を使う
                            }
                        } catch (ApiException e) {
                            Log.w(TAG, "Google sign in failed", e);
                            reauthForDelete = false;
                            Toast.makeText(this, Lang.t("Googleサインインに失敗しました", "Google sign-in failed"), Toast.LENGTH_SHORT).show();
                            updateHomeFragmentUI(null);
                        }
                    } else {
                        reauthForDelete = false;
                    }
                }
        );

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> Log.d(TAG, "POST_NOTIFICATIONS granted: " + granted)
        );

        // AdMob 初期化（別スレッドで）
        new Thread(() -> MobileAds.initialize(this, initializationStatus -> Log.d(TAG, "AdMob initialized")))
                .start();

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        refreshAdFree();
        ensureGuest();

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            binding.navView.setPadding(0, 0, 0, systemBars.bottom);
            return windowInsets;
        });

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                // 下のタブの画面だけを最上位にする（ほかの画面には戻る矢印を出す）
                R.id.navigation_home,
                R.id.navigation_favorites,
                R.id.navigation_entries,
                R.id.navigation_dashboard,
                R.id.navigation_horseMemos
        ).build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        setSupportActionBar(binding.toolbar);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        // ホームだけ、題名（推し馬ノート）の左に小さいアイコンを出す
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                binding.toolbar.setLogo(destination.getId() == R.id.navigation_home
                        ? ContextCompat.getDrawable(this, R.drawable.toolbar_logo) : null));
        NavigationUI.setupWithNavController(binding.navView, navController);
        // タブを押したら、そのタブの最初の画面を出す。
        // 標準の動きだと前に開いていた出走表などが復元され、ホームを押してもホームに戻れないため
        NavOptions tabOptions = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                .build();
        binding.navView.setOnItemSelectedListener(item -> {
            navController.navigate(item.getItemId(), null, tabOptions);
            return true;
        });
        binding.navView.setOnItemReselectedListener(item -> navController.navigate(item.getItemId(), null, tabOptions));

        // 通知チャンネル作成と通知権限のリクエスト
        NotificationHelper.createChannels(this);
        if (savedInstanceState == null) {
            requestNotificationPermissionIfNeeded();
        }

        // お気に入り馬の結果チェックを定期実行
        FavoriteResultWorker.schedule(this);
        if (savedInstanceState == null) {
            // ほかの画面で加えたお気に入りも、発走前の通知を早めに予約しておく
            FavoriteResultWorker.runOnce(this);
            handleNotificationIntent(getIntent());
        }

        // FCM トークン取得
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    Log.d("FCM Token", token);
                });
    }

    // 通知をタップしたときに開く画面（通知を作る側が extras に入れる）
    public static final String EXTRA_MEMO_HORSE = "memo_horse";
    public static final String EXTRA_MEMO_FROM = "memo_from";
    public static final String EXTRA_OPEN_RACE_DATE = "open_race_date";
    public static final String EXTRA_OPEN_RACE_COURSE = "open_race_course";
    public static final String EXTRA_OPEN_RACE_NO = "open_race_no";
    public static final String EXTRA_OPEN_RACE_TITLE = "open_race_title";

    /** タップでそのレースの出走表を開く通知用の extras */
    public static Bundle openRaceExtras(String date, String course, int raceNo, @Nullable String raceTitle) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_OPEN_RACE_DATE, date);
        extras.putString(EXTRA_OPEN_RACE_COURSE, course);
        extras.putInt(EXTRA_OPEN_RACE_NO, raceNo);
        extras.putString(EXTRA_OPEN_RACE_TITLE, raceTitle != null ? raceTitle : "");
        return extras;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    /** 通知から開かれたとき、通知が指定した画面を開く（1回だけ） */
    private void handleNotificationIntent(@Nullable Intent intent) {
        if (intent == null) return;
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        String memoHorse = intent.getStringExtra(EXTRA_MEMO_HORSE);
        String raceDate = intent.getStringExtra(EXTRA_OPEN_RACE_DATE);
        if (memoHorse != null) {
            String from = intent.getStringExtra(EXTRA_MEMO_FROM);
            navController.navigate(R.id.navigation_horseMemos);
            HorseMemos.load(memos -> {
                if (isFinishing() || isDestroyed()) return;
                HorseMemoDialog.show(this, new HashMap<>(memos), memoHorse, from, memo -> {
                });
            });
        } else if (raceDate != null) {
            Bundle args = new Bundle();
            args.putString(EntriesFragment.ARG_DATE, raceDate);
            args.putString(EntriesFragment.ARG_COURSE, intent.getStringExtra(EXTRA_OPEN_RACE_COURSE));
            args.putInt(EntriesFragment.ARG_RACE_NO, intent.getIntExtra(EXTRA_OPEN_RACE_NO, 0));
            args.putString(EntriesFragment.ARG_RACE_TITLE, intent.getStringExtra(EXTRA_OPEN_RACE_TITLE));
            navController.navigate(R.id.navigation_entryDetail, args);
        }
        // 画面を回転したときなどに、もう一度開かないようにする
        intent.removeExtra(EXTRA_MEMO_HORSE);
        intent.removeExtra(EXTRA_OPEN_RACE_DATE);
    }

    // アクションバー左の矢印で、ひとつ前の画面に戻る
    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        return navController.navigateUp() || super.onSupportNavigateUp();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /**
     * HomeFragmentから呼び出されるサインイン処理
     */
    public void signInWithGoogle() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                null,
                getMainExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        // 取得した Credential を処理
                        Credential credential = result.getCredential();
                        if (credential instanceof CustomCredential
                                && credential.getType().equals(TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                            try {
                                Bundle credentialData = credential.getData();
                                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credentialData);
                                String idToken = googleIdTokenCredential.getIdToken();
                                firebaseAuthWithGoogle(idToken);
                            } catch (Exception e) {
                                Log.e(TAG, "GoogleIdTokenCredential の作成に失敗", e);
                                reauthForDelete = false;
                                updateHomeFragmentUI(null);
                            }
                        } else {
                            Log.w(TAG, "取得した Credential が Google ID トークンではありませんでした。");
                            reauthForDelete = false;
                            Toast.makeText(MainActivity.this, Lang.t("ログインに失敗しました。", "Sign-in failed."), Toast.LENGTH_SHORT).show();
                            updateHomeFragmentUI(null);
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof NoCredentialException) {
                            Log.w(TAG, "保存済みの認証情報がありません。GoogleサインインUIを表示します。");
                            launchGoogleSignInFallback();
                        } else {
                            Log.e(TAG, "GetCredential failed", e);
                            reauthForDelete = false;
                            Toast.makeText(MainActivity.this, Lang.t("ログイン処理でエラーが発生しました。", "An error occurred while signing in."), Toast.LENGTH_SHORT).show();
                        }
                        updateHomeFragmentUI(null);
                    }
                }
        );
    }

    /**
     * ログアウト処理（HomeFragment から呼ばれる）
     */
    public void signOut() {
        // Firebase からログアウト
        mAuth.signOut();
        refreshAdFree();

        // ViewModel の UID をクリア
        SharedViewModel sharedViewModel = new ViewModelProvider(this).get(SharedViewModel.class);
        sharedViewModel.setUid(null);

        // HomeFragment の UI を未ログイン状態に更新
        updateHomeFragmentUI(null);

        Toast.makeText(this, Lang.t("ログアウトしました。", "Signed out."), Toast.LENGTH_SHORT).show();
        // ログアウトしてもお気に入りなどは使えるよう、新しい匿名アカウントで入り直す
        ensureGuest();
    }

    /**
     * 退会処理（HomeFragment で確認したあとに呼ばれる）。データとアカウントを消し、新しい匿名アカウントで入り直す
     */
    public void deleteAccount() {
        Toast.makeText(this, Lang.t("アカウントを削除しています…", "Deleting your account…"), Toast.LENGTH_SHORT).show();
        GuestAccount.deleteAccount(result -> {
            if (isFinishing() || isDestroyed()) return;
            switch (result) {
                case DELETED:
                    mAuth.signOut();
                    refreshAdFree();
                    new ViewModelProvider(this).get(SharedViewModel.class).setUid(null);
                    updateHomeFragmentUI(null);
                    Toast.makeText(this, Lang.t("アカウントを削除しました。", "Your account has been deleted."), Toast.LENGTH_LONG).show();
                    ensureGuest();
                    break;
                case NEEDS_SIGN_IN:
                    new AlertDialog.Builder(this)
                            .setTitle(Lang.t("本人確認", "Confirm it's you"))
                            .setMessage(Lang.t("アカウントを削除するには、もう一度 Google でログインしてください。",
                                    "To delete your account, please sign in with Google again."))
                            .setPositiveButton(Lang.t("ログイン", "Sign in"), (d, w) -> {
                                reauthForDelete = true;
                                signInWithGoogle();
                            })
                            .setNegativeButton(Lang.t("キャンセル", "Cancel"), null)
                            .show();
                    break;
                default:
                    Toast.makeText(this, Lang.t("アカウントを削除できませんでした。通信状態を確かめて、もう一度お試しください。",
                            "Couldn't delete your account. Check your connection and try again."), Toast.LENGTH_LONG).show();
                    break;
            }
        });
    }

    /** ログインしていなければ匿名で入る（お気に入り馬・メモ・予想をログインなしで使えるように） */
    private void ensureGuest() {
        GuestAccount.ensure((user, success) -> {
            if (user == null) return;
            new ViewModelProvider(this).get(SharedViewModel.class).setUid(user.getUid());
            updateHomeFragmentUI(user);
            refreshAdFree();
        });
    }

    /** 広告なしのアカウントか確かめ直し、変わっていれば表示中のバナーにも反映する */
    private void refreshAdFree() {
        AdFree.refresh(this, () -> AdFree.applyTo(getWindow().getDecorView()));
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        if (reauthForDelete) {
            reauthForDelete = false;
            GuestAccount.reauthenticate(credential, (user, success) -> {
                if (isFinishing()) return;
                if (success) {
                    deleteAccount();
                } else {
                    // 別の Google アカウントを選んだときなども、ここに来る
                    Toast.makeText(this, Lang.t("本人確認ができませんでした。ログイン中と同じ Google アカウントを選んでください。",
                            "Couldn't confirm it's you. Choose the Google account you're signed in with."), Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        // ログインせずに使っていたお気に入り・メモ・予想は、Google アカウントに引き継ぐ
        GuestAccount.signIn(credential, (user, success) -> {
            if (isFinishing()) return;
            // 認証完了後、HomeFragment に UI 更新を依頼
            updateHomeFragmentUI(user);
            if (success && user != null) {
                Log.d(TAG, "signInWithCredential:success");
                new ViewModelProvider(this).get(SharedViewModel.class).setUid(user.getUid());
                Toast.makeText(this, Lang.t("ログイン成功: ", "Signed in: ") + GuestAccount.displayName(user), Toast.LENGTH_SHORT).show();
                refreshAdFree();
            } else {
                Log.w(TAG, "signInWithCredential:failure");
                Toast.makeText(this, Lang.t("Firebaseでの認証に失敗しました。", "Firebase authentication failed."), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void launchGoogleSignInFallback() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);
        Intent signInIntent = googleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    /**
     * 表示されている HomeFragment を見つけて UI を更新する
     *
     * @param user ログインユーザー情報 (未ログインなら null)
     */
    private void updateHomeFragmentUI(FirebaseUser user) {
        try {
            Fragment navHostFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main);
            if (navHostFragment != null) {
                Fragment currentFragment = navHostFragment.getChildFragmentManager().getFragments().get(0);
                if (currentFragment instanceof HomeFragment) {
                    ((HomeFragment) currentFragment).updateUI(user);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update HomeFragment UI", e);
        }
    }
}
