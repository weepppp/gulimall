package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.Query;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jdk.nashorn.internal.scripts.JS;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    //    @Autowired
//    private CategoryDao categoryDao;
    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    RedissonClient redisson;


    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryEntity> listWithTree() {

        /**
         * @?????? [??????????????????????????????]
         */

        // 1?????????????????????
        List<CategoryEntity> entities = baseMapper.selectList(null);
        // 2?????????????????????
        // ???????????????????????????
        List<CategoryEntity> level1Menus = entities.stream().filter(categoryEntity ->
                categoryEntity.getParentCid() == 0
        ).map((menu) -> {
            menu.setChildren(getChildrens(menu, entities));
            return menu;
        }).sorted((menu1, menu2) -> {
            return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
        }).collect(Collectors.toList());

        return level1Menus;
    }

    @Override
    public Long[] findCatelogPath(Long cateId) {
        List<Long> paths = new ArrayList<>();
        List<Long> parentPath = findParentPath(cateId, paths);
        Collections.reverse(parentPath);
        return parentPath.toArray(new Long[parentPath.size()]);
    }


//    @Caching(evict = {
//            @CacheEvict(value = "category", key = "'getLevel1Categorys'"),
//            @CacheEvict(value = "category", key = "'getCatalogJson'"),
//    })
    @CacheEvict(value = "category",allEntries = true)  // ???????????????????????????????????????????????????  ?????????????????????????????????????????????????????????????????????????????????
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        /**
         * @?????? [?????????????????????????????????]
         */
        this.updateById(category);
        categoryBrandRelationService.updateCategory(category.getCatId(), category.getName());
    }

    @Cacheable(value = {"category"}, key = "#root.method.name")
    @Override
    public List<CategoryEntity> getLevel1Categorys() {
        Long l = System.currentTimeMillis();
        List<CategoryEntity> entities = this.baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));
        System.out.println("????????????" + (System.currentTimeMillis() - l));
        return entities;
    }

    // ??????redis???????????????????????????  ??????springCache
    // ???????????????????????????+????????????????????????????????????
    @Cacheable(value = {"category"}, key = "#root.method.name")
    @Override
    public Map<String, List<Catelog2Vo>> getCatalogJson() {
        List<CategoryEntity> level1Categorys = getLevel1Categorys();
        System.out.println(level1Categorys.toString());
        Map<String, List<Catelog2Vo>> map = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            List<CategoryEntity> entities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
            List<Catelog2Vo> catelog2VoList = null;
            if (entities != null) {
                catelog2VoList = entities.stream().map(item -> {
                    Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, item.getCatId().toString(), item.getName());
                    List<CategoryEntity> entities1 = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", item.getCatId()));
                    if (entities1 != null) {
                        List<Catelog2Vo.Catelog3Vo> catelog3VoList = entities1.stream().map(l3 -> {
                            Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(item.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3Vo;
                        }).collect(Collectors.toList());
                        catelog2Vo.setCatalog3List(catelog3VoList);
                    }
                    return catelog2Vo;
                }).collect(Collectors.toList());
            }
            return catelog2VoList;
        }));
        return map;
    }



    // ???????????????????????????????????????
    public Map<String, List<Catelog2Vo>> getCatalogJsonFromDb() {
        /**
         * @?????? ???????????????id+????????????????????????????????????
         */

        /**
         * @???????????? ????????????redisson?????????????????????
         */
        RLock lock = redisson.getLock("catalogJson-lock");
        lock.lock();
//      synchronized (this) {  // 1.>>>>>>?????????this???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
//        String s = UUID.randomUUID().toString();
//        Boolean lock = redisTemplate.opsForValue().setIfAbsent("lock", s, 300, TimeUnit.SECONDS); // 2.>>>>>>???????????????redis????????????+????????????????????????
        Map<String, List<Catelog2Vo>> stringListMap;
//        if (lock) {
        try {
            stringListMap = getStringListMap();
        } finally {
            lock.unlock();
//                //??????redis????????????   ???????????????+??????????????????=????????????>>????????????????????????????????????????????????
//                String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
//                Long lock1 = redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList("lock"), s);
//            }
//            return stringListMap;
//        } else {
//            // ?????????????????????
//            try {
//                Thread.sleep(200);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            return getCatalogJsonFromDb(); // ???????????????
        }
        return stringListMap;
    }

    private Map<String, List<Catelog2Vo>> getStringListMap() {
        String catalogJson = redisTemplate.opsForValue().get("catalogJson");
        if (!StringUtils.isEmpty(catalogJson)) {
            Map<String, List<Catelog2Vo>> stringListMap = JSON.parseObject(catalogJson, new TypeReference<Map<String, List<Catelog2Vo>>>() {
            });
            return stringListMap;
        }
        List<CategoryEntity> level1Categorys = getLevel1Categorys();
        System.out.println(level1Categorys.toString());
        Map<String, List<Catelog2Vo>> map = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            List<CategoryEntity> entities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
            List<Catelog2Vo> catelog2VoList = null;
            if (entities != null) {
                catelog2VoList = entities.stream().map(item -> {
                    Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, item.getCatId().toString(), item.getName());
                    List<CategoryEntity> entities1 = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", item.getCatId()));
                    if (entities1 != null) {
                        List<Catelog2Vo.Catelog3Vo> catelog3VoList = entities1.stream().map(l3 -> {
                            Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(item.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3Vo;
                        }).collect(Collectors.toList());
                        catelog2Vo.setCatalog3List(catelog3VoList);
                    }
                    return catelog2Vo;
                }).collect(Collectors.toList());
            }
            return catelog2VoList;
        }));
        String s = JSON.toJSONString(map);
        redisTemplate.opsForValue().set("catalogJson", s, 1, TimeUnit.DAYS);
        return map;
    }

    private List<Long> findParentPath(Long cateId, List<Long> paths) {
        paths.add(cateId);
        CategoryEntity id = this.getById(cateId);
        if (id.getParentCid() != 0) {
            findParentPath(id.getParentCid(), paths);
        }
        return paths;
    }

    // ????????????????????????????????????
    private List<CategoryEntity> getChildrens(CategoryEntity root, List<CategoryEntity> all) {
        List<CategoryEntity> children = all.stream().filter(categoryEntity -> {
            return categoryEntity.getParentCid() == root.getCatId();
        }).map(categoryEntity -> {
            categoryEntity.setChildren(getChildrens(categoryEntity, all));
            return categoryEntity;
        }).sorted((menu1, menu2) -> {
            return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
        }).collect(Collectors.toList());

        return children;
    }

}