package com.buzhidao.zzss.service;

import com.alibaba.fastjson.JSON;
import com.buzhidao.zzss.config.ApplicationConfig;
import com.buzhidao.zzss.response.MagnetPageSiteSort;
import com.buzhidao.zzss.response.MagnetRule;
import com.buzhidao.zzss.response.MagnetRulePath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

/**
 * 规则服务
 * created 2019/4/23 23:06
 */
@Service
@Slf4j
public class MagnetRuleService {

    @Autowired
    private ApplicationConfig config;

    private long lastLoadTime;

    private Map<String, MagnetRule> magnetRuleMap;
    private List<MagnetRule> sites;
    private List<String> trackers;
    private String trackersStr;

    /**
     * 重新加载规则
     *
     * @return
     */
    @PostConstruct
    public void reload() {
        magnetRuleMap = null;
        sites = null;
        getMagnetRule();

        reloadTrackers();
    }


    /**
     * 根据网站名称获取规则
     *
     * @param site
     * @return
     */
    public MagnetRule getRuleBySite(String site) {
        MagnetRule rule = getMagnetRule().get(site);
        if (rule == null) {
            return getSites().get(0);
        }
        return rule;
    }

    public List<MagnetRule> getSites() {
        return sites;
    }


    /**
     * 获取支持的排序方式
     *
     * @return
     */
    public List<MagnetPageSiteSort> getSupportedSorts(MagnetRulePath rulePaths) {
        List<MagnetPageSiteSort> options = new ArrayList<MagnetPageSiteSort>();
        if (!StringUtils.isEmpty(rulePaths.getPreset())) {
            options.add(new MagnetPageSiteSort(MagnetPageSiteSort.SORT_OPTION_DEFAULT));
        }
        if (!StringUtils.isEmpty(rulePaths.getTime())) {
            options.add(new MagnetPageSiteSort(MagnetPageSiteSort.SORT_OPTION_TIME));
        }
        if (!StringUtils.isEmpty(rulePaths.getSize())) {
            options.add(new MagnetPageSiteSort(MagnetPageSiteSort.SORT_OPTION_SIZE));
        }
        if (!StringUtils.isEmpty(rulePaths.getHot())) {
            options.add(new MagnetPageSiteSort(MagnetPageSiteSort.SORT_OPTION_HOT));
        }
        return options;
    }


    /**
     * 根据排序获取路径
     *
     * @param sortType
     * @return
     */
    public String getPathBySort(String sortType, MagnetRulePath rulePaths) {
        if (MagnetPageSiteSort.SORT_OPTION_SIZE.equals(sortType)) {
            return rulePaths.getSize();
        } else if (MagnetPageSiteSort.SORT_OPTION_HOT.equals(sortType)) {
            return rulePaths.getHot();
        } else if (MagnetPageSiteSort.SORT_OPTION_TIME.equals(sortType)) {
            return rulePaths.getTime();
        } else if (MagnetPageSiteSort.SORT_OPTION_DEFAULT.equals(sortType)) {
            return rulePaths.getPreset();
        } else {
            return getSupportedSorts(rulePaths).get(0).getSort();
        }
    }

    /**
     * 网站的筛选规则
     *
     * @return
     */
    private Map<String, MagnetRule> getMagnetRule() {
        if (magnetRuleMap == null || sites == null) {
            magnetRuleMap = new LinkedHashMap<String, MagnetRule>();
            sites = new ArrayList<MagnetRule>();

            List<MagnetRule> rules = loadMagnetRule();

            StringBuilder logsb = new StringBuilder();
            for (MagnetRule rule : rules) {
                if (config.proxyIgnore && rule.isProxy()) {
                    logsb.append("[忽略]--->").append(rule.getSite()).append(" : ").append(rule.getUrl()).append("\n");
                    continue;
                }
                try {
                    rule.setHost(new URL(rule.getUrl()).getHost());
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }

                magnetRuleMap.put(rule.getSite(), rule);
                sites.add(rule);
                logsb.append("[加载]--->").append(rule.getSite()).append(" : ").append(rule.getUrl()).append("\n");
            }
            logsb.append(rules.size());
            logsb.append("个网站规则加载完成，其中启用");
            logsb.append(magnetRuleMap.size());
            logsb.append("个，忽略");
            logsb.append(rules.size() - magnetRuleMap.size());
            logsb.append("个");
            log.info(logsb.toString());
        }
        return magnetRuleMap;
    }

    @Async
    public void reloadTrackers() {
        if (config.trackersEnabled) {
            trackers = loadTrackers();
            trackersStr = loadTrackersString();
            lastLoadTime = System.currentTimeMillis();
        }
    }

    public boolean isTrackersExpired() {
        return config.trackersEnabled && System.currentTimeMillis() >= lastLoadTime + config.trackersUpdateIntervalHour * 60 * 60 * 1000;
    }

    private List<String> loadTrackers() {
        String url = config.trackersUrl;
        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> response = rest.exchange(url, HttpMethod.GET, null, String.class);
        String body = response.getBody();
        String[] split = body.split("\n\n");

        log.info(String.format("加载%d个Tracker服务器", split.length));
        return Arrays.asList(split);
    }

    public String getTrackersString() {
        if (StringUtils.isEmpty(trackersStr)) {
            trackersStr = loadTrackersString();
        }
        return trackersStr;
    }

    public String loadTrackersString() {
        StringBuilder sb = new StringBuilder();
        String name = "&tr=";
        for (String it : trackers) {
            sb.append(name);
            sb.append(it);
        }
        return sb.toString();
    }

    private List<MagnetRule> loadMagnetRule() {
        if (config.isLocalRule()) {
            return loadResourceMagnetRule();
        } else {
            return requestMagnetRule();
        }
    }

    /**
     * 从本地资源获取规则
     *
     * @return
     */
    private List<MagnetRule> loadResourceMagnetRule() {
        InputStream inputStream = getClass().getResourceAsStream(String.format("/%s", config.ruleJsonUri));
        InputStreamReader isr = new InputStreamReader(inputStream, Charset.forName("UTF-8"));
        try {
            String s = IOUtils.toString(isr);
            return JSON.parseArray(s, MagnetRule.class);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return new ArrayList<MagnetRule>();
    }

    /**
     * 从网络上获取规则
     *
     * @return
     */
    private List<MagnetRule> requestMagnetRule() {
        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> response = rest.exchange(config.ruleJsonUri, HttpMethod.GET, null, String.class);
        return JSON.parseArray(response.getBody(), MagnetRule.class);
    }


}
