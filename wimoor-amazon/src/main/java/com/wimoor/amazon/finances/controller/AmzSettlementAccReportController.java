package com.wimoor.amazon.finances.controller;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wimoor.amazon.finances.pojo.dto.AmzSettlementDTO;
import com.wimoor.amazon.finances.service.IAmzFinAccountService;
import com.wimoor.amazon.finances.service.IAmzSettlementAccReportService;
import com.wimoor.amazon.finances.service.IAmzSettlementReportService;
import com.wimoor.common.GeneralUtil;
import com.wimoor.common.result.Result;
import com.wimoor.common.service.impl.SystemControllerLog;
import com.wimoor.common.user.UserInfo;
import com.wimoor.common.user.UserInfoContext;

import cn.hutool.core.util.StrUtil;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;

@Api(tags = "账期汇款记录")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/fin/settlementAccReport")
public class AmzSettlementAccReportController {
final IAmzSettlementAccReportService iAmzSettlementAccReportService;
final IAmzSettlementReportService iAmzSettlementReportService;
final IAmzFinAccountService iAmzFinAccountService;

@GetMapping("/getAccount")
public Result<?> getAccountAction(String currency ) {
	UserInfo user = UserInfoContext.get();
	String shopid = user.getCompanyid();
	Map<String, Map<String, Object>> list = iAmzFinAccountService.getFinancialByShopId(shopid, currency);
	return Result.success(list) ;
}

@ResponseBody
@RequestMapping("/getSettlementAccReport")
@SystemControllerLog("下载Acc")
public Result<?> getSettlementAccReportAction(@RequestBody AmzSettlementDTO dto) throws ParseException {
		Map<String, Object> map = new HashMap<String, Object>();
		 UserInfo user = UserInfoContext.get();
		String fromDate = dto.getFromDate();
		String fatype = dto.getFatype();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		if (StrUtil.isNotEmpty(fromDate)) {
			map.put("fromDate", sdf.format(sdf.parse(fromDate.trim())));
		} else {
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.MONTH, -1);
			fromDate = GeneralUtil.formatDate(cal.getTime(), sdf);
			map.put("fromDate", fromDate);
		}
		String endDate = dto.getEndDate();
		if (StrUtil.isNotEmpty(endDate)) {
			map.put("endDate", sdf.format(sdf.parse(endDate.trim())));
		} else {
			endDate = GeneralUtil.formatDate(new Date(), sdf);
			map.put("endDate", endDate);
		}
		String marketplace_name = dto.getMarketplace_name();
		if (StrUtil.isEmpty(marketplace_name) || "all".equals(marketplace_name)) {
			marketplace_name = null;
		}
		String groupid = dto.getGroupid();
		if (StrUtil.isEmpty(groupid) || "all".equals(groupid)) {
			groupid = null;
		}
		String currency = dto.getCurrency();
		String datetype = dto.getDatetype();
		if (StrUtil.isEmpty(datetype)) {
			datetype = null;
		}
		map.put("datetype", datetype);
		map.put("groupid", groupid);
		map.put("shopid", user.getCompanyid());
		map.put("marketplace_name", marketplace_name);
		map.put("currency", currency);
		if("finsett".equals(fatype)) {
			  IPage<Map<String, Object>> list = iAmzSettlementAccReportService.findSettlementAcc(dto.getPage(),map);
		      return Result.success(list);
		}else {
			 IPage<Map<String, Object>> list = iAmzFinAccountService.getFinancial(dto.getPage(),map);
			 return Result.success(list);
		}

}



		@SystemControllerLog( "下载sum")
		@PostMapping("/getSettlementAccReportSum")
		public Result<?> getSettlementAccReportSumAction(@RequestBody AmzSettlementDTO dto) throws ParseException {
			Map<String, Object> map = new HashMap<String, Object>();
			 UserInfo user = UserInfoContext.get();
			String fromDate = dto.getFromDate();
			String endDate = dto.getEndDate();
			String currency = dto.getCurrency();
			String datetype =dto.getDatetype();
			String marketplace_name = dto.getMarketplace_name();
			String groupid = dto.getGroupid();
			SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			if (StrUtil.isNotEmpty(fromDate)) {
				map.put("fromDate", sdf.format(sdf2.parse(fromDate.trim())));
			} else {
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.MONTH, -1);
				fromDate = GeneralUtil.formatDate(cal.getTime(), sdf);
				map.put("fromDate", fromDate);
			}

			if (StrUtil.isNotEmpty(endDate)) {
				map.put("endDate", sdf.format(sdf2.parse(endDate.trim())));
			} else {
				endDate = GeneralUtil.formatDate(new Date(), sdf);
				map.put("endDate", endDate);
			}
		
			if (StrUtil.isEmpty(marketplace_name) || "all".equals(marketplace_name)) {
				marketplace_name = null;
			}
	
			if (StrUtil.isEmpty(groupid) || "all".equals(groupid)) {
				groupid = null;
			}

			if (StrUtil.isEmpty(datetype)) {
				datetype = null;
			}
			map.put("datetype", datetype);
			map.put("groupid", groupid);
			map.put("shopid", user.getCompanyid());
			map.put("marketplace_name", marketplace_name);
			map.put("currency", currency);
			Map<String, Object> result1 = iAmzSettlementAccReportService.findSettlementAccSum(map);
			Map<String, Object> result2 = iAmzFinAccountService.getFinancialSum(map);
			BigDecimal acctotalsummary1 = (BigDecimal) result1.get("acctotalsummary");
			BigDecimal acctotalsummary2 = (BigDecimal) result2.get("acctotalsummary");
			Map<String,Object> result=new HashMap<String,Object>();
			String currencyicon =null;
			if(currency!=null) {
				currencyicon=GeneralUtil.formatCurrency(currency) ;
			}
			if(acctotalsummary2!=null) {
				result.put("finacc", currencyicon +GeneralUtil.formatterQuantity(acctotalsummary2));
			}else {
				result.put("finacc",  "0");
			}
			if(acctotalsummary1!=null) {
				result.put("finsett", currencyicon +GeneralUtil.formatterQuantity(acctotalsummary1));
			}else {
				result.put("finsett",  "0");
			}
			if(acctotalsummary2!=null&&acctotalsummary1!=null) {
				result.put("finsum", currencyicon +GeneralUtil.formatterQuantity(acctotalsummary1.add(acctotalsummary2)));
			}else {
				result.put("finsum",  "0");
			}
		    return Result.success(result);
		}
		
	@GetMapping("/faccdetail")
	public Result<?> faccDetailAction(String amazonauthid,String accgroupid,String nextToken) throws ParseException {
		UserInfo user = UserInfoContext.get();
		String shopid = user.getCompanyid();
		return Result.success(iAmzFinAccountService.getFaccDetial(shopid,amazonauthid,accgroupid,nextToken));
	}

}
