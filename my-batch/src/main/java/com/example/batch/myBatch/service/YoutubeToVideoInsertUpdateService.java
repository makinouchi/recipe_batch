package com.example.batch.myBatch.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.demo.common.DateUtil;
import com.example.demo.common.youtube.YoutubeCommon;
import com.example.demo.model.VideoHistory;
import com.example.demo.service.VideoHistoryService;
import com.example.demo.service.VideoService;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.ChannelStatistics;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;

/**
 * youtube data APIを利用し、youtubeデータにアクセス
 * youtubeデータを元にvideoを登録・更新。存在しないデータを登録。存在していれば更新
 * video_historyを実施日付で登録。再実行時は、存在しないものを登録
 */
@Service
public class YoutubeToVideoInsertUpdateService {

	private static final Logger logger = LoggerFactory.getLogger(YoutubeToVideoInsertUpdateService.class);

	@Autowired
	private VideoService videoService;

	@Autowired
	private VideoHistoryService videoHistoryService;

	// 発行したキー情報
	private final String API_KEY = "AIzaSyCUrIXiuJZfOsQATJGGKs1btSfN9dAMcFw";

	// りゅうじのチャンネル
	private final String CHANNEL_ID = "UCW01sMEVYQdhcvkrhbxdBpw";
	private final String APPLICATION_NAME = "YouTubeSearchExample";

	// youtubeデータをクラス変数に保持
	private Map<LocalDate, List<PlaylistItem>> cacheMap = new ConcurrentHashMap<>();
	
	private List<PlaylistItem> getCachedVideosByDate(LocalDate date, String id, YouTube youtube) throws IOException {
	    if(cacheMap.containsKey(date)) {
	    	logger.info("YoutubeToVideoInsertUpdate getCachedVideosByDate キャッシュを利用");
	        return cacheMap.get(date);
	    } else {
	    	logger.info("YoutubeToVideoInsertUpdate getCachedVideosByDate APIで取得");
	        List<PlaylistItem> videos = getVideoId(id, youtube);
	        cacheMap.put(date, videos);
	        return videos;
	    }
	}
	
	/**
	 * 非同期で実行
	 * Demo1Application　に「@EnableAsync」を定義
	 * @throws Exception
	 */
	public void executeBatch() {
		logger.info("YoutubeToVideoInsertUpdate start ---------------------------");
		try {
			YouTube service = new YouTube.Builder(
					GoogleNetHttpTransport.newTrustedTransport(),
					GsonFactory.getDefaultInstance(),
					request -> {
					}).setApplicationName(APPLICATION_NAME).build();

			String uploadsPlaylistId = getPlayListId(service);
			// TODO 試行錯誤してからこっちにする
			//List<PlaylistItem> list = getCachedVideosByDate(LocalDate.now(), uploadsPlaylistId, service);
			List<PlaylistItem> list = getVideoId(uploadsPlaylistId, service);
			
			VideoHistory videoHistory = new VideoHistory();
			videoHistory.setTargetDate(LocalDateTime.now());
			// 今日日付のvideoIdの一覧を取得
			List<String> videosHistoryVideoIdList = videoHistoryService.searchVideoIdList(videoHistory);
			List<String> videosVideoIdList = videoService.searchVideoIdList();
			
			List<VideoHistory> videoHistoryList = new ArrayList<VideoHistory>();
			List<com.example.demo.model.Video> videoInsertList = new ArrayList<com.example.demo.model.Video>();
			List<com.example.demo.model.Video> videoUpdateList = new ArrayList<com.example.demo.model.Video>();
			
			int count = 0;

			for (PlaylistItem data : list) {
				logger.info("YoutubeToVideoInsertUpdate videoId : " + data.getContentDetails().getVideoId());

				com.example.demo.model.Video insData = new com.example.demo.model.Video();
				List<Video> videoList = getViews(data.getContentDetails().getVideoId(), service);

				for (Video vData : videoList) {
					// ループして値を設定
					insData.setVideoId(data.getContentDetails().getVideoId());
					insData.setTitle(vData.getSnippet().getTitle());
					insData.setDescription(vData.getSnippet().getDescription());
					insData.setViewCount(vData.getStatistics().getViewCount());
					insData.setFavoriteCount(vData.getStatistics().getFavoriteCount());
					insData.setCommentCount(vData.getStatistics().getCommentCount());

					DateTime dateTime = data.getContentDetails().getVideoPublishedAt();
					LocalDateTime localDateTime = DateUtil.toLocalDateTime(dateTime);

					insData.setPublishedAt(localDateTime);
					insData.setUpdateAt(LocalDateTime.now());

					// サムネ画像URLのStandardのアクセス
					if (vData.getSnippet().getThumbnails().getStandard() != null
							&& vData.getSnippet().getThumbnails().getStandard().getUrl() != null) {
						insData.setThumbnailUrl(vData.getSnippet().getThumbnails().getStandard().getUrl());
					}

					// [video_history]登録処理 ===================================
					// videos_historyデータの登録（1日1回実行想定）
					VideoHistory input = new VideoHistory();
					input.setVideoId(data.getContentDetails().getVideoId());
					input.setTargetDate(LocalDateTime.now());

					// videoIdとtargetDateが同じデータが存在するか確認
					//List<VideoHistory> videoHistoryList = videoHistoryService.search(input);

					//if (videoHistoryList != null && videoHistoryList.size() > 0) {
					if (videosHistoryVideoIdList.contains(data.getContentDetails().getVideoId())) {
						// 日付毎の再生数用に登録されたvideo_historyデータが既に存在する場合は、1回処理済みということで、次の行へ
						continue;
					}

					// 登録がない場合、登録
					//insertVideoHistory(data, vData);
					VideoHistory videoHistoryInsertData = new VideoHistory();
					videoHistoryInsertData.setVideoId(data.getContentDetails().getVideoId());

					videoHistoryInsertData.setTargetDate(LocalDateTime.now());

					videoHistoryInsertData.setViewCount(vData.getStatistics().getViewCount());
					videoHistoryInsertData.setFavoriteCount(vData.getStatistics().getFavoriteCount());
					videoHistoryInsertData.setCommentCount(vData.getStatistics().getCommentCount());

					// 登録
					//videoHistoryService.insert(data);
					// あとで一括登録用にリストに追加
					videoHistoryList.add(videoHistoryInsertData);


					// [videos]登録・更新処理 ===================================
					// 検索
					com.example.demo.model.Video inputData = new com.example.demo.model.Video();
					inputData.setVideoId(data.getContentDetails().getVideoId());
					// レシピ内容を設定
					insData.setRecipeNote(YoutubeCommon.getRecipeNote(insData.getDescription()));

					//List<com.example.demo.model.Video> searchList = videoService.search(inputData);
					//if (searchList != null && searchList.size() > 0) {
					if (videosVideoIdList.contains(data.getContentDetails().getVideoId())) {
						// データがあるので、更新

						// 再生数_前日増加数
//						insData.setDailyViewCountDiff(
//								vData.getStatistics().getViewCount().subtract(searchList.get(0).getViewCount()));
						// コメント数_前日増加数
//						insData.setDailyCommentCountDiff(
//								vData.getStatistics().getCommentCount().subtract(searchList.get(0).getCommentCount()));
						//videoService.update(insData);
						// update用のリストに追加
						videoUpdateList.add(insData);

					} else {

						// 登録
						insData.setInsertAt(LocalDateTime.now());
						//videoService.insert(insData);
						// insert用のリストに追加
						videoInsertList.add(insData);
					}
					
					logger.info("YoutubeToVideoInsertUpdate count : " + count);
					count++;
					if (count >= 1000) {
						// videosの処理が1000件行ったら、処理を終わる
						break;
					}
				}
				
				if (count >= 1000) {
					// videosの処理が1000件行ったら、処理を終わる
					break;
				}
			}
			
			// 100件毎に処理
			if (!videoHistoryList.isEmpty()) {
				List<List<VideoHistory>> batches = ListUtils.partition(videoHistoryList, 100);
				for (List<VideoHistory> batch : batches) {
					videoHistoryService.insert(batch);
				}
			}
			
			if (!videoInsertList.isEmpty()) {
				List<List<com.example.demo.model.Video>> batches = ListUtils.partition(videoInsertList, 100);
				for (List<com.example.demo.model.Video> batch : batches) {
					videoService.insert(batch);
				}
			}
			
			if (!videoUpdateList.isEmpty()) {
				List<List<com.example.demo.model.Video>> batches = ListUtils.partition(videoUpdateList, 100);
				for (List<com.example.demo.model.Video> batch : batches) {
					videoService.update(batch);
				}
			}
			
			getStatistics(service);
			logger.info("YoutubeToVideoInsertUpdate end ---------------------------");

		} catch (Exception e) {
			logger.error("YoutubeToVideoInsertUpdate 非同期処理で例外発生", e);
		}
	}

	/**
	 * 部分的に更新したい時に呼び出して更新
	 * 
	 * @throws Exception
	 */
	@Async
	public void updateVideos() throws Exception {
		YouTube service = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(),
				GsonFactory.getDefaultInstance(),
				request -> {
				}).setApplicationName(APPLICATION_NAME).build();

		String uploadsPlaylistId = getPlayListId(service);
		List<PlaylistItem> list = getVideoId(uploadsPlaylistId, service);

		for (PlaylistItem data : list) {
			com.example.demo.model.Video insData = new com.example.demo.model.Video();
			List<Video> videoList = getViews(data.getContentDetails().getVideoId(), service);

			for (Video vData : videoList) {
				// ループして値を設定
				insData.setVideoId(data.getContentDetails().getVideoId());

				// サムネ画像URLのStandardのアクセス
				if (vData.getSnippet().getThumbnails().getStandard() != null
						&& vData.getSnippet().getThumbnails().getStandard().getUrl() != null) {
					insData.setThumbnailUrl(vData.getSnippet().getThumbnails().getStandard().getUrl());
				}

				// 検索
				com.example.demo.model.Video inputData = new com.example.demo.model.Video();
				inputData.setVideoId(data.getContentDetails().getVideoId());

				List<com.example.demo.model.Video> searchList = videoService.search(inputData);
				if (searchList != null && searchList.size() > 0) {
					// データがあるので、更新

					if (vData.getSnippet().getThumbnails().getStandard() != null
							&& vData.getSnippet().getThumbnails().getStandard().getUrl() != null) {
						videoService.update(insData);
					}
				}
			}
		}
		getStatistics(service);
	}

	/**
	 * 動画IDを指定して、動画IDに対する概要を更新する
	 * ※文字化けした際に再取り込みするのに利用した
	 * 
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public void videoIdDataUpdate(String videoId) throws GeneralSecurityException, IOException {
		YouTube youtube = new YouTube.Builder(
				GoogleNetHttpTransport.newTrustedTransport(),
				GsonFactory.getDefaultInstance(),
				request -> {
				}).setApplicationName("youtube-ingredients-extractor").build();

		YouTube.Videos.List request = youtube.videos()
				.list("snippet")
				.setKey(API_KEY)
				.setId(videoId);

		VideoListResponse response = request.execute();
		List<Video> videos = response.getItems();

		if (videos.isEmpty()) {
			logger.error("動画が見つかりませんでした。");
			return;
		}

		String description = videos.get(0).getSnippet().getDescription();
		com.example.demo.model.Video insData = new com.example.demo.model.Video();
		insData.setVideoId(videoId);
		// 検索
		List<com.example.demo.model.Video> searchList = videoService.search(insData);
		searchList.get(0).setDescription(description);
		videoService.update(searchList.get(0));
	}

	/**
	 * video_history　登録
	 * 
	 * @param item
	 * @param vData
	 */
	private void insertVideoHistory(PlaylistItem item, Video vData) {
		VideoHistory data = new VideoHistory();
		data.setVideoId(item.getContentDetails().getVideoId());

		data.setTargetDate(LocalDateTime.now());

		data.setViewCount(vData.getStatistics().getViewCount());
		data.setFavoriteCount(vData.getStatistics().getFavoriteCount());
		data.setCommentCount(vData.getStatistics().getCommentCount());

		// 登録
		videoHistoryService.insert(data);
	}

	/**
	 * チャネルIDよりプレイリストIDを取得
	 * 
	 * @param service
	 * @return
	 * @throws IOException
	 */
	private String getPlayListId(YouTube service) throws IOException {
		YouTube.Channels.List channelRequest = service.channels().list("contentDetails");
		channelRequest.setId(CHANNEL_ID);
		channelRequest.setKey(API_KEY);

		ChannelListResponse channelResponse = channelRequest.execute();
		String uploadsPlaylistId = channelResponse.getItems().get(0)
				.getContentDetails().getRelatedPlaylists().getUploads();

		logger.info(uploadsPlaylistId);
		return uploadsPlaylistId;
	}

	/**
	 * プレイリストIDから動画ID（videoId）を取得
	 * 
	 * @param playlistId
	 * @param service
	 * @throws IOException
	 */
	private List<PlaylistItem> getVideoId(String playlistId, YouTube service) throws IOException {
		YouTube.PlaylistItems.List playlistRequest = service.playlistItems().list("snippet,contentDetails");
		playlistRequest.setPlaylistId(playlistId);
		playlistRequest.setMaxResults(50L);
		playlistRequest.setKey(API_KEY);

		PlaylistItemListResponse playlistResponse;
		String nextPageToken = null;
		List<String> videoIds = new ArrayList<>();
		List<PlaylistItem> playlistItemList = new ArrayList<>();
		int count = 0;

		do {
			if (nextPageToken != null) {
				// 次ページのトークンを再設定
				playlistRequest.setPageToken(nextPageToken);
			}

			playlistResponse = playlistRequest.execute();

			for (PlaylistItem item : playlistResponse.getItems()) {
				playlistItemList.add(item);
				String videoId = item.getContentDetails().getVideoId();
				videoIds.add(videoId);
				logger.info(count + " videoId : " + videoId);
				count++;
			}

			logger.info("Fetched: " + playlistResponse.getItems().size());
			logger.info("Next Page Token: " + nextPageToken);

			// 次ページのトークンを取得。なければ終了
			nextPageToken = playlistResponse.getNextPageToken();

		} while (nextPageToken != null);
		return playlistItemList;

	}

	/**
	 * 動画IDから動画情報．再生数を取得
	 * 
	 * @param videoId
	 * @param service
	 * @throws IOException
	 */
	private List<Video> getViews(String videoId, YouTube service) throws IOException {
		YouTube.Videos.List videoRequest = service.videos().list("snippet,statistics");
		videoRequest.setId(String.join(",", videoId));
		videoRequest.setKey(API_KEY);

		VideoListResponse videoResponse = videoRequest.execute();
		List<Video> videoList = videoResponse.getItems();

		for (Video video : videoList) {
			String title = video.getSnippet().getTitle();
			long views = video.getStatistics().getViewCount().longValue();

			String description = video.getSnippet().getDescription();

			List<String> list = video.getSnippet().getTags();
			if (list != null) {
				for (String tag : list) {

				}
			}
		}

		return videoList;

	}

	/**
	 * チャネルのstatistics情報を取得
	 * ログに取得した内容を出力
	 * 
	 * @param service
	 * @throws IOException
	 */
	private void getStatistics(YouTube service) throws IOException {
		YouTube.Channels.List channelRequest = service.channels().list("statistics");
		channelRequest.setId(CHANNEL_ID);
		channelRequest.setKey(API_KEY);

		ChannelListResponse response = channelRequest.execute();
		ChannelStatistics stats = response.getItems().get(0).getStatistics();

		logger.info("チャネル情報 -------------------");
		logger.info("総再生回数: " + stats.getViewCount());
		logger.info("登録者数: " + stats.getSubscriberCount());
		logger.info("動画数: " + stats.getVideoCount());

	}

}
