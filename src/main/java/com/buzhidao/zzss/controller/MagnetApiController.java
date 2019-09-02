package com.buzhidao.zzss.controller;

import com.buzhidao.zzss.config.ApplicationConfig;
import com.buzhidao.zzss.exception.MagnetParserException;
import com.buzhidao.zzss.handler.PermissionHandler;
import com.buzhidao.zzss.response.*;
import com.buzhidao.zzss.service.FilterService;
import com.buzhidao.zzss.service.MagnetRuleService;
import com.buzhidao.zzss.service.MagnetService;
import com.buzhidao.zzss.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

/**
 * created 2019/05/05 12:04
 */
@RestController
@RequestMapping("api")
@Slf4j
public class MagnetApiController {
    @Autowired
    ApplicationConfig config;

    @Autowired
    PermissionService permissionService;

    @Autowired
    MagnetRuleService ruleService;

    @Autowired
    MagnetService magnetService;

    @Autowired
    FilterService filterService;


    /**
     * 重载配置
     *
     * @return
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping(value = "reload", method = RequestMethod.GET)
    public BaseResponse reload(@RequestParam(value = "p") String password) throws Exception {
        return permissionService.runAsPermission(password, "规则重载成功", new PermissionHandler<Void>() {
            public Void onPermissionGranted() {
                ruleService.reload();
                return null;
            }
        });
    }

    /**
     * 清除缓存
     *
     * @return
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping(value = "clear-cache", method = RequestMethod.GET)
    public BaseResponse clearCache(@RequestParam(value = "p") String password) throws Exception {
        return permissionService.runAsPermission(password, "缓存清除成功", new PermissionHandler<Void>() {

            public Void onPermissionGranted() {
                magnetService.clearCache();
                return null;
            }
        });
    }


    /**
     * 获取源站列表
     *
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "source", method = RequestMethod.GET)
    public BaseResponse<List<MagnetRule>> source() throws Exception {
        List<MagnetRule> sites = ruleService.getSites();
        return BaseResponse.success(sites, String.format("%d个规则加载成功", sites.size()));
    }

    /**
     * 搜索
     *
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "search", method = RequestMethod.GET)
    public BaseResponse<MagnetPageData> search(HttpServletRequest request, @RequestParam(required = false) String source, @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String sort, @RequestParam(required = false) Integer page) throws MagnetParserException, IOException {
        //是否需要屏蔽
        if (config.resultFilterEnabled && filterService.contains(keyword)) {
            log.info("搜索结果被屏蔽--->" + keyword);
            return BaseResponse.error("搜索结果被屏蔽");
        }

        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

        //默认参数
        MagnetPageOption pageOption = magnetService.transformCurrentOption(source, keyword, sort, page);
        MagnetRule rule = ruleService.getRuleBySite(pageOption.getSite());

        List<MagnetItem> infos = magnetService.parser(rule, pageOption.getKeyword(), pageOption.getSort(), pageOption.getPage(), userAgent);

        MagnetPageData data = new MagnetPageData();
        data.setTrackersString(ruleService.getTrackersString());
        //如果过期了就重新异步缓存Tracker服务器列表
        if (ruleService.isTrackersExpired()) {
            ruleService.reloadTrackers();
        }
        data.setCurrent(pageOption);
        data.setResults(infos);

        if (config.preloadEnabled && infos.size() > 0) {
            magnetService.asyncPreloadNextPage(rule, pageOption, userAgent);
        }
        return BaseResponse.success(data, String.format("搜索到%d条结果", infos.size()));
    }

    /**
     * 过滤
     *
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "filter", method = RequestMethod.POST)
    public BaseResponse<MagnetPageData> filter(@RequestParam String input) {
        if (StringUtils.isEmpty(input)) {
            return BaseResponse.error("请输入关键词或磁力链");
        } else {
            try {
                boolean add = filterService.add(input);
                if (add) {
                    return BaseResponse.success(null, "添加成功");
                } else {
                    return BaseResponse.error("服务器繁忙");
                }
            } catch (Exception e) {
                return BaseResponse.error(e.getMessage());
            }

        }
    }


}
