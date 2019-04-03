package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final int RC_SIGN_IN = 2;
    public static final int PICK_IMAGE = 1;
    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

    private ProgressBar mProgressBar;
    private EditText mMessageEditText;
    private Button mSendButton;

    private List<FriendlyMessage> friendlyMessages;
    private MessageAdapter mMessageAdapter;

    private StorageReference storageReference;
    private DatabaseReference messageDatabaseRef;
    private ChildEventListener childEventListener;

    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FirebaseRemoteConfig remoteConfig;

    private String mUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
        storageReference = firebaseStorage.getReference().child("chat_photos");
        remoteConfig = FirebaseRemoteConfig.getInstance();

        FirebaseDatabase messageDatabase = FirebaseDatabase.getInstance();
        messageDatabaseRef = messageDatabase.getReference().child("messages");

        firebaseAuth = FirebaseAuth.getInstance();
        mUsername = ANONYMOUS;

        // Initialize references to views
        mProgressBar =  findViewById(R.id.progressBar);
        RecyclerView mMessageRecyclerView = findViewById(R.id.messageRecyclerView);
        ImageButton mPhotoPickerButton = findViewById(R.id.photoPickerButton);
        mMessageEditText =  findViewById(R.id.messageEditText);
        mSendButton =  findViewById(R.id.sendButton);

        friendlyMessages = new ArrayList<>();
        //set layout manager
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setOrientation(LinearLayoutManager.VERTICAL);
        mMessageRecyclerView.setLayoutManager(manager);
        //set Adapter
        mMessageAdapter = new MessageAdapter(friendlyMessages);
        mMessageRecyclerView.setAdapter(mMessageAdapter);


        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(intent, 1);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(),
                        mUsername, null);
                messageDatabaseRef.push().setValue(friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });


        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null){
                    //user is signed in
                    onSignInInitialize(user.getDisplayName());
                } else {
                    //user is signed out
                    onSignOutCleanUp();
                    startActivityForResult(
                        AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setIsSmartLockEnabled(false, true)
                            .setAvailableProviders(
                                    Arrays.asList(
                                            new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.EmailBuilder().build()
                                    )
                            )
                            .build()
                        ,RC_SIGN_IN);
                }
            }
        };

        FirebaseRemoteConfigSettings remoteConfigSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();

        remoteConfig.setConfigSettings(remoteConfigSettings);

        Map<String , Object> remoteMap = new HashMap<>();
        remoteMap.put(FRIENDLY_MSG_LENGTH_KEY ,DEFAULT_MSG_LENGTH_LIMIT);
        remoteConfig.setDefaults(remoteMap);

        fetchRemoteConfig();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE && data != null){
            Uri uri = data.getData();
            assert uri != null;

            final StorageReference fileReference = storageReference.child(System.currentTimeMillis() + ".jpeg");
            StorageTask<UploadTask.TaskSnapshot> uploadTask = fileReference.putFile(uri);

            uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    if (!task.isSuccessful()) {
                        if (task.getException() != null)
                        throw task.getException();
                    }
                    return fileReference.getDownloadUrl();
                }
            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();
                        FriendlyMessage friendlyMessage = new FriendlyMessage(null,
                                mUsername, downloadUri.toString());
                        messageDatabaseRef.push().setValue(friendlyMessage);
                    } else {
                        Toast.makeText(MainActivity.this,"Upload failed", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        if (requestCode == RC_SIGN_IN){
            if (resultCode == RESULT_OK){
                Toast.makeText(this ,"Signed in" , Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED){
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.sign_out_menu :
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        firebaseAuth.addAuthStateListener(authStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (authStateListener != null) {
            firebaseAuth.removeAuthStateListener(authStateListener);
        }
        friendlyMessages.clear();
        mMessageAdapter.notifyDataSetChanged();
        detachReadListenerFromDatabase();
    }

    private void onSignInInitialize(String username){
        mUsername = username;
        attachReadListenerToDatabase();
    }

    private void onSignOutCleanUp(){
        mUsername = ANONYMOUS;
        friendlyMessages.clear();
        mMessageAdapter.notifyDataSetChanged();
        detachReadListenerFromDatabase();
    }

    private void attachReadListenerToDatabase(){
        if (childEventListener == null) {
            childEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    friendlyMessages.add(friendlyMessage);
                }
                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }
                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                }
                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };
            messageDatabaseRef.addChildEventListener(childEventListener);
        }
    }

    private void detachReadListenerFromDatabase(){
        if (childEventListener != null){
            messageDatabaseRef.removeEventListener(childEventListener);
            childEventListener = null;
        }
    }

    private void fetchRemoteConfig(){
        long cacheXpiration = 3600;

        if (remoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()){
            cacheXpiration = 0;
        }

        remoteConfig.fetch(cacheXpiration)
        .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                remoteConfig.activateFetched();
                applyRetrievedLengthLimitToEdittext();
            }
        })
        .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.i(TAG,"error fetching config");
                applyRetrievedLengthLimitToEdittext();
            }
        });
    }

    private void applyRetrievedLengthLimitToEdittext(){
        long length_limit = remoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
        mMessageEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(((int) length_limit))
        });
    }
}
