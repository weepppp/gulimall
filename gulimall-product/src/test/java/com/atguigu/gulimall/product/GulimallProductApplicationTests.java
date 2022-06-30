package com.atguigu.gulimall.product;

import com.aliyun.oss.*;
import com.atguigu.gulimall.product.entity.BrandEntity;
import com.atguigu.gulimall.product.service.BrandService;
import com.atguigu.gulimall.product.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;


@SpringBootTest
class GulimallProductApplicationTests {

    @Autowired
    BrandService brandService;

    @Autowired
    CategoryService categoryService;

//    @Test
//    public void testFindPath(){
//        Long[] catelogPath = categoryService.findCatelogPath(225L);
//        log.info("完整路径:{}", Arrays.asList(catelogPath));
//    }

    @Test
    void contextLoads() {
        BrandEntity brandEntity = new BrandEntity();
        brandEntity.setBrandId(1L);
        brandEntity.setName("特斯拉");
        brandService.updateById(brandEntity);
        System.out.println("修改成功......");
    }



    /**
     * @功能  [测试阿里云OSS对象存储]
     */

    @Autowired
    OSSClient ossClient;


    @Test
    public void testUpload() throws FileNotFoundException {
        // Endpoint以华东1（杭州）为例，其它Region请按实际情况填写。
//        String endpoint = "oss-cn-hangzhou.aliyuncs.com";
//        // 阿里云账号AccessKey拥有所有API的访问权限，风险很高。强烈建议您创建并使用RAM用户进行API访问或日常运维，请登录RAM控制台创建RAM用户。
//        String accessKeyId = "LTAI5t9fTxvFehy3mp8pvga9";
//        String accessKeySecret = "2pgJOHko4yrbafRoSBE74xMiCYceMZ";
        // 填写Bucket名称，例如examplebucket。
//        String bucketName = "examplebucket";
        // 填写Object完整路径，完整路径中不能包含Bucket名称，例如exampledir/exampleobject.txt。
//        String objectName = "exampledir/exampleobject.txt";
        // 填写本地文件的完整路径，例如D:\\localpath\\examplefile.txt。
        // 如果未指定本地路径，则默认从示例程序所属项目对应本地路径中上传文件流。
//        String filePath= "D:\\localpath\\examplefile.txt";

        // 创建OSSClient实例。
//        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

//        try {
            InputStream inputStream = new FileInputStream("C:\\Users\\zoeak\\Desktop\\Pictures\\002.png");
            // 创建PutObject请求。
            ossClient.putObject("gulimall0822", "002.png", inputStream);
//        } catch (OSSException oe) {
//            System.out.println("Caught an OSSException, which means your request made it to OSS, "
//                    + "but was rejected with an error response for some reason.");
//            System.out.println("Error Message:" + oe.getErrorMessage());
//            System.out.println("Error Code:" + oe.getErrorCode());
//            System.out.println("Request ID:" + oe.getRequestId());
//            System.out.println("Host ID:" + oe.getHostId());
//        } catch (ClientException | FileNotFoundException ce) {
//            System.out.println("Caught an ClientException, which means the client encountered "
//                    + "a serious internal problem while trying to communicate with OSS, "
//                    + "such as not being able to access the network.");
//            System.out.println("Error Message:" + ce.getMessage());
//        } finally {
//            if (ossClient != null) {
                ossClient.shutdown();
        System.out.println("上传完成");
            }
        }

