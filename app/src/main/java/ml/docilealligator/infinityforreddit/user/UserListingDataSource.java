package ml.docilealligator.infinityforreddit.user;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PageKeyedDataSource;

import java.util.List;
import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.NetworkState;
import ml.docilealligator.infinityforreddit.thing.SortType;
import retrofit2.Retrofit;

public class UserListingDataSource extends PageKeyedDataSource<String, UserData> {

    private final Executor executor;
    private final Handler handler;
    private final Retrofit retrofit;
    private final String query;
    private final SortType sortType;
    private final boolean nsfw;

    private final MutableLiveData<NetworkState> paginationNetworkStateLiveData;
    private final MutableLiveData<NetworkState> initialLoadStateLiveData;
    private final MutableLiveData<Boolean> hasUserLiveData;

    private PageKeyedDataSource.LoadParams<String> params;
    private PageKeyedDataSource.LoadCallback<String, UserData> callback;

    UserListingDataSource(Executor executor, Handler handler, Retrofit retrofit, String query, SortType sortType, boolean nsfw) {
        this.executor = executor;
        this.handler = handler;
        this.retrofit = retrofit;
        this.query = query;
        this.sortType = sortType;
        this.nsfw = nsfw;
        paginationNetworkStateLiveData = new MutableLiveData<>();
        initialLoadStateLiveData = new MutableLiveData<>();
        hasUserLiveData = new MutableLiveData<>();
    }

    MutableLiveData<NetworkState> getPaginationNetworkStateLiveData() {
        return paginationNetworkStateLiveData;
    }

    MutableLiveData<NetworkState> getInitialLoadStateLiveData() {
        return initialLoadStateLiveData;
    }

    MutableLiveData<Boolean> hasUserLiveData() {
        return hasUserLiveData;
    }

    @Override
    public void loadInitial(@NonNull PageKeyedDataSource.LoadInitialParams<String> params, @NonNull PageKeyedDataSource.LoadInitialCallback<String, UserData> callback) {
        initialLoadStateLiveData.postValue(NetworkState.LOADING);

        FetchUserData.fetchUserListingData(executor, handler, retrofit, query, null, sortType.getType(), nsfw,
                new FetchUserData.FetchUserListingDataListener() {
                    @Override
                    public void onFetchUserListingDataSuccess(List<UserData> UserData, String after) {
                        hasUserLiveData.postValue(!UserData.isEmpty());

                        callback.onResult(UserData, null, after);
                        initialLoadStateLiveData.postValue(NetworkState.LOADED);
                    }

                    @Override
                    public void onFetchUserListingDataFailed() {
                        initialLoadStateLiveData.postValue(new NetworkState(NetworkState.Status.FAILED, "Error retrieving ml.docilealligator.infinityforreddit.User list"));
                    }
                });
    }

    @Override
    public void loadBefore(@NonNull PageKeyedDataSource.LoadParams<String> params, @NonNull PageKeyedDataSource.LoadCallback<String, UserData> callback) {

    }

    @Override
    public void loadAfter(@NonNull PageKeyedDataSource.LoadParams<String> params, @NonNull PageKeyedDataSource.LoadCallback<String, UserData> callback) {
        this.params = params;
        this.callback = callback;

        if (params.key.equals("null") || params.key.isEmpty()) {
            return;
        }

        FetchUserData.fetchUserListingData(executor, handler, retrofit, query, params.key, sortType.getType(), nsfw,
                new FetchUserData.FetchUserListingDataListener() {
                    @Override
                    public void onFetchUserListingDataSuccess(List<UserData> UserData, String after) {
                        callback.onResult(UserData, after);
                        paginationNetworkStateLiveData.postValue(NetworkState.LOADED);
                    }

                    @Override
                    public void onFetchUserListingDataFailed() {
                        paginationNetworkStateLiveData.postValue(new NetworkState(NetworkState.Status.FAILED, "Error retrieving ml.docilealligator.infinityforreddit.User list"));
                    }
                });
    }

    void retryLoadingMore() {
        loadAfter(params, callback);
    }
}
