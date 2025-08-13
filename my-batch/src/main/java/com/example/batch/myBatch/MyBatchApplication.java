package com.example.batch.myBatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;

import com.example.batch.myBatch.service.YoutubeToVideoInsertUpdateService;

@SpringBootApplication
@ComponentScan(basePackages = {
	    "com.example.batch",         // 自分のバッチのパッケージ
	    "com.example.demo.service",  // VideoService のあるパッケージ
	    "com.example.demo.common",   // 共通クラス等があれば追加
	    "com.example.demo.model"     // モデルクラス等があれば追加
	})
@MapperScan("com.example.demo.mapper")
public class MyBatchApplication implements CommandLineRunner {
	
	@Autowired
	private YoutubeToVideoInsertUpdateService youtubeToVideoInsertUpdateService;
	
    public static void main(String[] args) {
    	new SpringApplicationBuilder(MyBatchApplication.class)
        .web(WebApplicationType.NONE)  // Webサーバーを無効化
        .run(args);
    }

	@Override
	public void run(String... args) throws Exception {
		youtubeToVideoInsertUpdateService.executeBatch();
	}
}