package com.example.batch.myBatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;

import com.example.demo.shell.YoutubeToVideoInsertUpdate;

@SpringBootApplication
// アプリ側に存在するコンポーネントのクラスを利用する場合、クラスのパッケージを指定しておく必要がある
@ComponentScan(basePackages = {
	    "com.example.batch",
	    "com.example.demo.service",
	    "com.example.demo.common",
	    "com.example.demo.model",
	    "com.example.demo.shell"
	})
@MapperScan("com.example.demo.mapper")
public class MyBatchApplication implements CommandLineRunner {
	
	@Autowired
	private YoutubeToVideoInsertUpdate youtubeToVideoInsertUpdate;
	
    public static void main(String[] args) {
    	new SpringApplicationBuilder(MyBatchApplication.class)
        .web(WebApplicationType.NONE)  // Webサーバーを無効化
        .run(args);
    }

	@Override
	public void run(String... args) throws Exception {
		youtubeToVideoInsertUpdate.run(args[0]);
	}
}