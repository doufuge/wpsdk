# weplay-demo-game

## How to use it

In MainActivity
```shell
    ...
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // WePlaySDK initialize.
        WePlaySdk.initialize("Your Game ID", this);
    }
    ...
```

```shell
    ...
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // WePlaySDK request file read permission.
        WePlaySdk.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    ...
```

## Report score
```shell
    // Double score
    WePlaySdk.reportScore(score, new WPCallback() {
        @Override
        public void onSuccess() {
            // Report success
        }

        @Override
        public void onFailure(String err) {
            // Report failure
        }
    });
```

