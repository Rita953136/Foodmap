package com.example.fmap.ui.home;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fmap.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class SignupActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private MaterialButton btnSignup;
    private TextView tvLogin;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);   // ← 你的這份 XML 檔名請存為 activity_signup.xml

        auth = FirebaseAuth.getInstance();

        etEmail   = findViewById(R.id.signup_email);
        etPassword= findViewById(R.id.signup_password);
        btnSignup = findViewById(R.id.signup_button);
        tvLogin   = findViewById(R.id.loginRedirectText);

        btnSignup.setOnClickListener(v -> doSignup());
        // 「Already an user? Login」→ 回上一頁（LoginActivity）
        tvLogin.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
    }

    private void doSignup() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString();

        if (TextUtils.isEmpty(email)) { etEmail.setError("請輸入 Email"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { etEmail.setError("Email 格式不正確"); return; }
        if (TextUtils.isEmpty(pass)) { etPassword.setError("請輸入密碼"); return; }
        if (pass.length() < 6) { etPassword.setError("密碼至少 6 碼"); return; }

        btnSignup.setEnabled(false);
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
            btnSignup.setEnabled(true);
            if (task.isSuccessful()) {
                Toast.makeText(this, "註冊成功", Toast.LENGTH_SHORT).show();
                setResult(Activity.RESULT_OK);  // 通知 LoginActivity 成功
                finish();
            } else {
                String msg = task.getException() != null ? task.getException().getMessage() : "註冊失敗";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}

