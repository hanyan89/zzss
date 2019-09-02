package com.buzhidao.zzss.controller;

import com.alibaba.fastjson.JSON;
import com.buzhidao.zzss.config.ApplicationConfig;
import com.buzhidao.zzss.response.MagnetPageConfig;
import com.buzhidao.zzss.response.MagnetPageData;
import com.buzhidao.zzss.response.MagnetPageOption;
import com.buzhidao.zzss.response.MagnetRule;
import com.buzhidao.zzss.service.MagnetRuleService;
import com.buzhidao.zzss.service.MagnetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * created 2019/5/5 17:53
 */
@Controller
@RequestMapping("")
public class IndexController {
    @Autowired
    ApplicationConfig config;

    @Autowired
    MagnetRuleService ruleService;

    @Autowired
    MagnetService magnetService;

    @RequestMapping(value = {"", "search"}, method = RequestMethod.GET)
    public String search(HttpServletRequest request, Model model, @RequestParam(required = false) String source, @RequestParam(value = "k", required = false) String keyword,
                         @RequestParam(value = "s", required = false) String sort, @RequestParam(value = "p", required = false) Integer page) throws Exception {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        boolean isMobile = !StringUtils.isEmpty(userAgent) && userAgent.toLowerCase().contains("mobile");

        //默认参数
        MagnetPageOption pageOption = magnetService.transformCurrentOption(source, keyword, sort, page);
        if (isMobile) {
            //手机的初始化不能跨页
            pageOption.setPage(1);
        }
        MagnetPageData data = new MagnetPageData();
        data.setCurrent(pageOption);

        MagnetRule rule = ruleService.getRuleBySite(source);

        model.addAttribute("is_mobile", isMobile);
        model.addAttribute("current", JSON.toJSONString(pageOption));
        model.addAttribute("config", JSON.toJSONString(new MagnetPageConfig(config)));
        model.addAttribute("sort_by", JSON.toJSONString(ruleService.getSupportedSorts(rule.getPaths())));
        model.addAttribute("source_sites", JSON.toJSONString(ruleService.getSites()));

        return isMobile ? "mobile" : "index";
    }


}
