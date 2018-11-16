package org.wordpress.android.ui.stats.refresh.usecases

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.whenever
import kotlinx.coroutines.experimental.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.wordpress.android.BaseUnitTest
import org.wordpress.android.R
import org.wordpress.android.R.string
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.stats.TagsModel
import org.wordpress.android.fluxc.model.stats.TagsModel.TagModel
import org.wordpress.android.fluxc.store.InsightsStore
import org.wordpress.android.fluxc.store.InsightsStore.OnInsightsFetched
import org.wordpress.android.fluxc.store.InsightsStore.StatsError
import org.wordpress.android.fluxc.store.InsightsStore.StatsErrorType.GENERIC_ERROR
import org.wordpress.android.test
import org.wordpress.android.ui.stats.refresh.BlockListItem
import org.wordpress.android.ui.stats.refresh.BlockListItem.ExpandableItem
import org.wordpress.android.ui.stats.refresh.BlockListItem.Item
import org.wordpress.android.ui.stats.refresh.BlockListItem.Link
import org.wordpress.android.ui.stats.refresh.BlockListItem.Title
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.EXPANDABLE_ITEM
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.ITEM
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.LINK
import org.wordpress.android.ui.stats.refresh.BlockListItem.Type.TITLE
import org.wordpress.android.ui.stats.refresh.Failed
import org.wordpress.android.ui.stats.refresh.InsightsItem
import org.wordpress.android.ui.stats.refresh.InsightsItem.Type.FAILED
import org.wordpress.android.ui.stats.refresh.InsightsItem.Type.LIST_INSIGHTS
import org.wordpress.android.ui.stats.refresh.ListInsightItem
import org.wordpress.android.viewmodel.ResourceProvider

class TagsAndCategoriesUseCaseTest : BaseUnitTest() {
    @Mock lateinit var insightsStore: InsightsStore
    @Mock lateinit var site: SiteModel
    @Mock lateinit var resourceProvider: ResourceProvider
    private lateinit var useCase: TagsAndCategoriesUseCase
    @Before
    fun setUp() {
        useCase = TagsAndCategoriesUseCase(
                Dispatchers.Unconfined,
                insightsStore,
                resourceProvider
        )
    }

    @Test
    fun `maps tags to UI model`() = test {
        val forced = false
        val categoryName = "category name"
        whenever(resourceProvider.getString(eq(R.string.stats_category_folded_name), any(), any())).thenReturn(
                categoryName
        )
        val singleTagViews: Long = 10
        val firstTag = TagModel.Item("tag1", "tag", "url.com")
        val secondTag = TagModel.Item("tag2", "tag", "url2.com")
        val singleTag = TagModel(listOf(firstTag), singleTagViews)
        val categoryViews: Long = 15
        val category = TagModel(
                listOf(firstTag, secondTag),
                categoryViews
        )
        whenever(insightsStore.fetchTags(site, forced)).thenReturn(
                OnInsightsFetched(
                        TagsModel(listOf(singleTag, category))
                )
        )

        val result = loadTags(true, forced)

        assertThat(result.type).isEqualTo(LIST_INSIGHTS)
        (result as ListInsightItem).apply {
            assertThat(this.items).hasSize(4)
            assertTitle(this.items[0])
            assertSingleTag(this.items[1], firstTag.name, singleTagViews.toString())
            assertCategory(this.items[2], categoryName, categoryViews)
                    .apply {
                        assertThat(this.expandedItems).hasSize(2)
                        assertSingleTag(this.expandedItems[0], firstTag.name, null)
                        assertSingleTag(this.expandedItems[1], secondTag.name, null)
                    }
            assertLink(this.items[3])
        }
    }

    @Test
    fun `maps empty tags to UI model`() = test {
        val forced = false
        whenever(insightsStore.fetchTags(site, forced)).thenReturn(
                OnInsightsFetched(TagsModel(listOf()))
        )

        val result = loadTags(true, forced)

        assertThat(result.type).isEqualTo(LIST_INSIGHTS)
        (result as ListInsightItem).apply {
            assertThat(this.items).hasSize(2)
            assertTitle(this.items[0])
            assertThat(this.items[1]).isEqualTo(BlockListItem.Empty)
        }
    }

    @Test
    fun `maps error item to UI model`() = test {
        val forced = false
        val message = "Generic error"
        whenever(insightsStore.fetchTags(site, forced)).thenReturn(
                OnInsightsFetched(
                        StatsError(GENERIC_ERROR, message)
                )
        )

        val result = loadTags(true, forced)

        assertThat(result.type).isEqualTo(FAILED)
        (result as Failed).apply {
            assertThat(this.failedType).isEqualTo(string.stats_view_tags_and_categories)
            assertThat(this.errorMessage).isEqualTo(message)
        }
    }

    private fun assertTitle(item: BlockListItem) {
        assertThat(item.type).isEqualTo(TITLE)
        assertThat((item as Title).text).isEqualTo(R.string.stats_view_tags_and_categories)
    }

    private fun assertSingleTag(
        item: BlockListItem,
        key: String,
        label: String?
    ) {
        assertThat(item.type).isEqualTo(ITEM)
        assertThat((item as Item).text).isEqualTo(key)
        if (label != null) {
            assertThat(item.value).isEqualTo(label)
        } else {
            assertThat(item.value).isNull()
        }
        assertThat(item.icon).isEqualTo(R.drawable.ic_tag_grey_dark_24dp)
    }

    private fun assertCategory(
        item: BlockListItem,
        label: String,
        views: Long
    ): ExpandableItem {
        assertThat(item.type).isEqualTo(EXPANDABLE_ITEM)
        assertThat((item as ExpandableItem).header.text).isEqualTo(label)
        assertThat(item.header.value).isEqualTo(views.toString())
        assertThat(item.header.icon).isEqualTo(R.drawable.ic_folder_multiple_grey_dark_24dp)
        return item
    }

    private fun assertLink(item: BlockListItem) {
        assertThat(item.type).isEqualTo(LINK)
        assertThat((item as Link).text).isEqualTo(R.string.stats_insights_view_more)
    }

    private suspend fun loadTags(refresh: Boolean, forced: Boolean): InsightsItem {
        var result: InsightsItem? = null
        useCase.liveData.observeForever { result = it }
        useCase.fetch(site, refresh, forced)
        return checkNotNull(result)
    }
}