package com.wimoor.common.service.impl;

import cn.hutool.core.lang.Assert;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.UploadObjectArgs;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.*;

//@Component
@Slf4j
//@ConfigurationProperties(prefix = "aliyun")
public class OSSApiService3 implements InitializingBean {



	@Setter
	private String oss_endpoint;

	@Setter
	private String accessKeyId;

	@Setter
	private String accessKeySecret;

	@Setter
	public  String bucketName;

	@Setter
	public  String bucketPath;

//    @Setter
//    public  String bucketPath;

	MinioClient minioClient =
			MinioClient.builder()
					.endpoint(oss_endpoint)
					.credentials(accessKeyId, accessKeySecret)
					.build();


    @Override
    public void afterPropertiesSet() {
        Assert.notBlank(oss_endpoint, "minio 地址为空");
        Assert.notBlank(accessKeyId, "minio 账号为空");
        Assert.notBlank(accessKeySecret, "minio 密码为空");
    }


	/**
	 * 上传文件
	 * @param objectName
	 * @param stream 可以是已下方式
	 * InputStream inputStream = new URL(url).openStream();
	 * ByteArrayInputStream inputStream=new ByteArrayInputStream(content.getBytes())
	 * InputStream inputStream = new FileInputStream(filePath);
	 *
	 */
	 public  Boolean putObject(String bucketName,String objectName,InputStream stream){
		 File tempFile = null;
		 OutputStream fileOutputStream = null;
		 byte[] buffer = new byte[1024];
		 try {
			 fileOutputStream = new FileOutputStream(tempFile);
			 int length;
			 while ((length = stream.read(buffer)) > 0) {
				 fileOutputStream.write(buffer, 0, length);
			 }
			 UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
					 .bucket(bucketName)
					 .object(objectName)
					 .filename(tempFile.getAbsolutePath())
					 .build();
			 //上传
			 minioClient.uploadObject(uploadObjectArgs);
             log.debug("上传文件到minio成功,bucket:{},objectName:{},文件名字:{}",bucketName,objectName);
			 return true;
		 } catch (Exception e) {
			 e.printStackTrace();
			 log.error("上传文件出错,bucket:{},objectName:{},错误信息:{}",bucketName,objectName,e.getMessage());
		 }finally {
			 try {
				 stream.close();
				 fileOutputStream.close();
			 } catch (IOException e) {
				 throw new RuntimeException(e);
			 }
		 }
		 return false;
	}


	public void removeObject(String bucketName, String objectName) {

		try {
			RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build();
			minioClient.removeObject(removeObjectArgs);
		} catch (Exception e) {
			log.error("删除文件出错,bucket:{},objectName:{},错误信息:{}",bucketName,objectName,e.getMessage());
			throw new RuntimeException(e);
		}
	}



	public String getBucketName() {
		return bucketName;
	}


}
