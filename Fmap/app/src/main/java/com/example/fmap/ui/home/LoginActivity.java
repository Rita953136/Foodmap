package com.example.fmap.ui.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fmap.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;         // ← 改用官方按鈕
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private EditText loginEmail, loginPassword;
    private TextView signupRedirectText, forgotPassword;
    private Button loginButton;
    private SignInButton googleBtn;                       // ← 這裡改型別

    private FirebaseAuth auth;
    private GoogleSignInClient gClient;

    // Google 登入
    private ActivityResultLauncher<Intent> googleLauncher;
    // Sign up
    private ActivityResultLauncher<Intent> signupLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        // 若已登入，直接結束並把結果帶回 MainActivity
        if (auth.getCurrentUser() != null) {
            returnSuccess();
            return;
        }

        // Google Sign-In（一定要 requestIdToken 才能換 Firebase 憑證）
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        gClient = GoogleSignIn.getClient(this, gso);

        // 註冊 Google 登入結果
        googleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        toast("已取消 Google 登入");
                        return;
                    }
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount acc = task.getResult(ApiException.class);
                        AuthCredential cred = GoogleAuthProvider.getCredential(acc.getIdToken(), null);
                        auth.signInWithCredential(cred).addOnCompleteListener(signTask -> {
                            if (signTask.isSuccessful()) {
                                returnSuccess(); // 回 MainActivity，由它用 FirebaseAuth 更新 UI
                            } else {
                                toast("Google 登入失敗，請再試一次");
                            }
                        });
                    } catch (ApiException e) {
                        toast("Google 登入取消/失敗");
                    }
                });

        // 註冊 Sign up 結果
        signupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // 註冊成功後 Firebase 會已有使用者，直接回 MainActivity
                        returnSuccess();
                    }
                });

        bindViews();
        // 以程式設定按鈕外觀，避免 XML 屬性相容性問題
        googleBtn.setSize(SignInButton.SIZE_WIDE);
        googleBtn.setColorScheme(SignInButton.COLOR_DARK);

        bindActions();
    }

    private void bindViews() {
        loginEmail = findViewById(R.id.login_email);
        loginPassword = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_button);
        signupRedirectText = findViewById(R.id.signUpRedirectText);
        forgotPassword = findViewById(R.id.forgot_password);
        googleBtn = findViewById(R.id.googleBtn);            // XML 也要是 com.google.android.gms.common.SignInButton
    }

    private void bindActions() {
        loginButton.setOnClickListener(v -> doEmailLogin());

        // 走 launcher，註冊成功會一路回到 MainActivity
        signupRedirectText.setOnClickListener(v ->
                signupLauncher.launch(new Intent(this, SignupActivity.class)));

        // 如要啟用忘記密碼，把下一行打開，並還原 showForgotDialog()
//        forgotPassword.setOnClickListener(v -> showForgotDialog());

        googleBtn.setOnClickListener(v ->
                googleLauncher.launch(gClient.getSignInIntent()));
    }

    /** Email/密碼登入 */
    private void doEmailLogin() {
        String email = loginEmail.getText().toString().trim();
        String pass = loginPassword.getText().toString();

        if (TextUtils.isEmpty(email)) { loginEmail.setError("請輸入 Email"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { loginEmail.setError("Email 格式不正確"); return; }
        if (TextUtils.isEmpty(pass)) { loginPassword.setError("請輸入密碼"); return; }

        loginButton.setEnabled(false);
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> returnSuccess())
                .addOnFailureListener(e -> {
                    loginButton.setEnabled(true);
                    toast("Email 登入失敗");
                });
    }

    /** 登入/註冊成功：只回傳 RESULT_OK，MainActivity 再用 FirebaseAuth 更新 UI */
    private void returnSuccess() {
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}
