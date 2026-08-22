package io.github.eonewg.gnome.data.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.skydoves.sandwich.getOrThrow
import com.skydoves.sandwich.mapSuccess
import io.github.eonewg.gnome.data.model.Memo
import io.github.eonewg.gnome.data.remote.RemoteDataSource

const val EXPLORE_PAGE_SIZE = 20

class ExplorePagingSource(
    private val remoteRepository: RemoteDataSource
) : PagingSource<String, Memo>() {

    override fun getRefreshKey(state: PagingState<String, Memo>): String? {
        return null
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Memo> {
        val response = remoteRepository.listWorkspaceMemos(EXPLORE_PAGE_SIZE, params.key)

        return try {
            response.mapSuccess {
                LoadResult.Page(
                    data = this.first,
                    prevKey = null,
                    nextKey = this.second,
                )
            }.getOrThrow()
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
