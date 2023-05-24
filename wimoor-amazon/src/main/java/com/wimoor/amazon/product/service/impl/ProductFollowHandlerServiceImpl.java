package com.wimoor.amazon.product.service.impl;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.Resource;

import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wimoor.amazon.auth.pojo.entity.AmazonAuthority;
import com.wimoor.amazon.auth.pojo.entity.FollowOffer;
import com.wimoor.amazon.auth.pojo.entity.Marketplace;
import com.wimoor.amazon.auth.service.IAmazonAuthorityService;
import com.wimoor.amazon.auth.service.IFollowOfferService;
import com.wimoor.amazon.auth.service.IMarketplaceService;
import com.wimoor.amazon.notifications.service.IAwsSQSMessageHandlerService;
import com.wimoor.amazon.product.mapper.ProductFollowMapper;
import com.wimoor.amazon.product.pojo.entity.FollowOfferChange;
import com.wimoor.amazon.product.pojo.entity.ProductFollow;
import com.wimoor.amazon.product.service.IFollowOfferChangeService;
import com.wimoor.amazon.product.service.IProductFollowHandlerService;
import com.wimoor.amazon.util.HttpUtils;
import com.wimoor.common.GeneralUtil;
import com.wimoor.util.FTPServerUtil;
 

@Service("productFollowHandlerService")
public class ProductFollowHandlerServiceImpl  extends ServiceImpl<ProductFollowMapper, ProductFollow> implements IProductFollowHandlerService,IAwsSQSMessageHandlerService { 

	@Resource
	private IMarketplaceService marketplaceService;
	@Resource
	private IAmazonAuthorityService amazonAuthorityService;
	@Resource
	IFollowOfferService followOfferService;
	@Resource
	IFollowOfferChangeService followOfferChangeService;

	
	public List<Marketplace> marketplacelist = new ArrayList<Marketplace>();
	
	public void recordProductFollow(String authid,String asin,String marketplaceid ,Date timeofChange,Integer offernumber){
		ProductFollow productFollow = this.baseMapper.selectByMarketplaceAsin(asin, marketplaceid, authid);
		if (productFollow == null) {
			if(offernumber == 1) {
				return;
			}
			productFollow = new ProductFollow();
			productFollow.setAsin(asin);
			productFollow.setAmazonAuthId(authid);
			productFollow.setMarketplaceid(marketplaceid);
			productFollow.setFlownumber(offernumber);
			productFollow.setLastupdateTime(timeofChange);
			this.baseMapper.insert(productFollow);
		} else {
			productFollow.setFlownumber(offernumber);
			productFollow.setLastupdateTime(timeofChange);
			this.baseMapper.updateById(productFollow);
		}
	}
	
	public void recordFollowOffer(JSONArray offers, String marketplaceid) {
		if(offers == null ) {
			return;
		}
		for(int i =0 ;i < offers.size(); i++) {
			 JSONObject offerinfo = offers.getJSONObject(i);
			 
			Integer sellerPositiveFeedbackRating = 0;
			Integer feedbackCount = 0;
			String sellerid =offerinfo.getString("SellerId");
			JSONObject sellerFeedbackRating=offerinfo.getJSONObject("SellerFeedbackRating");
			if( sellerFeedbackRating!= null) {
				sellerPositiveFeedbackRating = sellerFeedbackRating.getIntValue("SellerPositiveFeedbackRating");
				feedbackCount =	sellerFeedbackRating.getIntValue("FeedbackCount"); ;
			}
			FollowOffer followOffer = null;
			QueryWrapper<FollowOffer> query=new QueryWrapper<FollowOffer>();
			query.eq("sellerid", sellerid);
			query.eq("marketplaceid", marketplaceid);
			followOffer = followOfferService.getOne(query);
			if(followOffer==null) {
				followOffer = new FollowOffer();
			}
			followOffer.setMarketplaceid(marketplaceid);
			followOffer.setPositiveFeedbackRating(sellerPositiveFeedbackRating);
			followOffer.setFeedbackCount(feedbackCount);
			followOffer.setName(null);
			if(followOffer.getSellerid() != null) {
				followOfferService.update(followOffer,query);
			} else {
				followOffer.setSellerid(sellerid);
				followOfferService.save(followOffer);
			}
		}
	}
	
	public void recordFollowOfferChange(JSONArray offers, String asin, String marketplaceid) {
 
		
		Boolean ftype = false;
		List<FollowOfferChange> lostList = followOfferChangeService.selectAllForLostOffer(asin, marketplaceid);
		List<String> selleridList = new ArrayList<String>();
		if(lostList == null || lostList.size() == 0) {
			ftype = true;
		}
		if(offers==null) {
			return;
		}
		for(int i = 0; i < offers.size(); i++) {
			 JSONObject offerinfo = offers.getJSONObject(i);
			String sellerid = offerinfo.getString("SellerId");
			Boolean isFulfilledByAmazon =offerinfo.getBooleanValue("IsFulfilledByAmazon");
			Boolean isBuyBoxWinner =offerinfo.getBooleanValue("IsBuyBoxWinner");
			Boolean isFeaturedMerchant =offerinfo.getBooleanValue("IsFeaturedMerchant");
			Boolean shipsDomestically =offerinfo.getBooleanValue("ShipsDomestically");
			JSONObject listingPrice=offerinfo.getJSONObject("ListingPrice");
			BigDecimal listingPriceAmount =listingPrice.getBigDecimal("Amount");
			JSONObject shipping=offerinfo.getJSONObject("Shipping");
			BigDecimal shipingAmount = shipping.getBigDecimal("Amount");
			String currency = listingPrice.getString("CurrencyCode");
			JSONObject primeInformation=offerinfo.getJSONObject("PrimeInformation");
			
			Boolean isPrime = primeInformation.getBooleanValue("IsOfferPrime");
			Boolean isNationalPrime = primeInformation.getBooleanValue("IsOfferNationalPrime");
			
			if(ftype) {
				FollowOfferChange followOfferChange = followOfferChangeService.selectOfferChangeByLost(sellerid, asin, marketplaceid);
				if(followOfferChange != null && followOfferChange.getLosttime() != null) {
					Date lostTime = followOfferChange.getLosttime();
					if(GeneralUtil.distanceOfMinutes(lostTime, new Date()) > 5) {
						followOfferChange = new FollowOfferChange();
					}else {
						followOfferChange.setLosttime(null);
					}
				}else {
					followOfferChange = new FollowOfferChange();
				}
				//FollowOfferChange followOfferChange = new FollowOfferChange();
				if(selleridList != null && selleridList.size() > 0) {
					if(selleridList.contains(sellerid)) {
						followOfferChange = followOfferChangeService.selectOfferChangeBySellerAsin(sellerid, asin, marketplaceid);
					}
				}
				followOfferChange.setAsin(asin);
				followOfferChange.setCurrency(currency);
				followOfferChange.setIsBuyBoxWinner(isBuyBoxWinner);
				followOfferChange.setIsFeaturedMerchant(isFeaturedMerchant);
				followOfferChange.setIsFulfilledByAmazon(isFulfilledByAmazon);
				followOfferChange.setIsNationalPrime(isNationalPrime);
				followOfferChange.setIsPrime(isPrime);
				followOfferChange.setListingPriceAmount(listingPriceAmount);
				followOfferChange.setMarketplaceid(marketplaceid);
				followOfferChange.setSellerid(sellerid);
				followOfferChange.setShipingAmount(shipingAmount);
				followOfferChange.setShipsDomestically(shipsDomestically);
				if(followOfferChange.getFindtime() == null) {
					followOfferChange.setFindtime(new Date());
					followOfferChangeService.save(followOfferChange);
				}else {
					followOfferChangeService.updateById(followOfferChange);
				}
				sendProductFollowWxMsg(sellerid);
				selleridList.add(sellerid);
			}else {
				FollowOfferChange followOfferChange = followOfferChangeService.selectOfferChangeBySellerAsin(sellerid, asin, marketplaceid);
				if(followOfferChange == null) {
					followOfferChange = followOfferChangeService.selectOfferChangeByLost(sellerid, asin, marketplaceid);
					if(followOfferChange != null && followOfferChange.getLosttime() != null) {
						Date lostTime = followOfferChange.getLosttime();
						if(GeneralUtil.distanceOfMinutes(lostTime, new Date()) > 5) {
							followOfferChange = new FollowOfferChange();
						}else {
							followOfferChange.setLosttime(null);
						}
					}else {
						followOfferChange = new FollowOfferChange();
					}
				}
				followOfferChange.setAsin(asin);
				followOfferChange.setCurrency(currency);
				followOfferChange.setIsBuyBoxWinner(isBuyBoxWinner);
				followOfferChange.setIsFeaturedMerchant(isFeaturedMerchant);
				followOfferChange.setIsFulfilledByAmazon(isFulfilledByAmazon);
				followOfferChange.setIsNationalPrime(isNationalPrime);
				followOfferChange.setIsPrime(isPrime);
				followOfferChange.setListingPriceAmount(listingPriceAmount);
				followOfferChange.setMarketplaceid(marketplaceid);
				followOfferChange.setSellerid(sellerid);
				followOfferChange.setShipingAmount(shipingAmount);
				followOfferChange.setShipsDomestically(shipsDomestically);
				if(followOfferChange.getFindtime() == null) {
					followOfferChange.setFindtime(new Date());
					followOfferChangeService.save(followOfferChange);
				}else {
					followOfferChangeService.updateById(followOfferChange);
					for(int j = 0; j <lostList.size(); j++) {
						FollowOfferChange followOfferChangeLost = lostList.get(j);
						if(followOfferChangeLost.getSellerid().equals(followOfferChange.getSellerid())) {
							lostList.remove(followOfferChangeLost);
							break;
						}
					}
				}
				sendProductFollowWxMsg(sellerid);
			}
		}
		if(lostList != null && lostList.size() > 0) {
			for(FollowOfferChange followOfferChangeLost : lostList) {
				followOfferChangeLost.setLosttime(new Date());
				followOfferChangeService.updateById(followOfferChangeLost);
			}
		}
	}
	public List<Marketplace> getAllMarketplace() {
		if(marketplacelist.size()==0){
			marketplacelist = marketplaceService.findAllMarketplace();
		}
		return marketplacelist;
	}
	
	public boolean isMarketplaceid(String sellerid) {
		for (int i = 0; i < marketplacelist.size(); i++) {
			if(marketplacelist.get(i).getMarketplaceid().equalsIgnoreCase(sellerid)){
				return true;
			}
		}
		return false;
	}
	
	public void recordFollowOfferSellerName() {//一小时执行一次
		getAllMarketplace();
		List<Map<String,Object>> list = followOfferService.getNeedUpdate();
		if(list!=null && list.size()>0){
			for (int i = 0; i < list.size(); i++) {
				Map<String,Object> followOffer = list.get(i);
				String sellerName = null;
				String sellerid = followOffer.get("sellerid").toString();
				String refreshtime = followOffer.get("refreshtime")==null ? null : followOffer.get("refreshtime").toString();
				int refreshnum = followOffer.get("refreshnum")==null? 0 : Integer.parseInt(followOffer.get("refreshnum").toString());
				//更新时间大于24h的，重置更新次数
				if(refreshtime!=null){
					Calendar now = Calendar.getInstance();
					now.add(Calendar.DAY_OF_MONTH, -1);
					Date lasttime = GeneralUtil.parseDate(refreshtime);
					if(lasttime.before(now.getTime())){
						refreshnum=0;
					}
				}
				//如果更新次数大于10次，则24h内不再发送更新请求
				if(refreshnum>=10){
					continue;
				} 
				if(followOffer.get("point_name")==null){
					continue;
				}
				if(isMarketplaceid(sellerid)){
					sellerName="Amazon";
				} else {
					String point_name = followOffer.get("point_name").toString();
					sellerName = catchSellerName(sellerid,point_name);
				}
				if("".equals(sellerName)){
					sellerName = null;
				}
				try {
					followOfferService.updateSellerName(sellerid, sellerName, refreshnum+1);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			System.out.println(new Date()+"更新完成。");
		} 
	}
	
	public String catchSellerName(String sellerid, String point_name) {
		String sellerName = null;
		String remoteUrl = "https://www." + point_name.toLowerCase() + "/sp?seller=" + sellerid;
		org.jsoup.nodes.Document document = null;
		try {
			 HttpUtils httpsUtils = HttpUtils.getInstance();
			try {
				document = httpsUtils.getHtmlPageResponseAsDocument(remoteUrl);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
		if(document != null){
			Element h1 = document.getElementById("sellerName");
			if(h1 != null) {
				sellerName = h1.html().trim();
			}
		}
		return sellerName;
	}
	
	public String getSellerName(String sellerid, String marketplaceid) {
		 
		QueryWrapper<FollowOffer> query=new QueryWrapper<FollowOffer>();
		query.eq("sellerid", sellerid);
		query.eq("marketplaceid", marketplaceid);
		FollowOffer followOff = followOfferService.getOne(query);
		String sellerName = null;
		if(followOff != null ) {
			sellerName = followOff.getName();
		}
		if(sellerName==null){
			Marketplace marketplace = marketplaceService.getById(marketplaceid);
			if(marketplace==null){
				return null;
			}
			sellerName = catchSellerName(sellerid, marketplace.getPointName().toLowerCase());
		}
		return sellerName;
	}

	public static String saveTxtByFollow(String marketplaceid, String sku, String authid, String html) {
		String contextpath = null;
		FTPServerUtil ftputil=new FTPServerUtil();
		try {
			contextpath =  "amazon_follow_html/" +  authid+"/"+marketplaceid+"/"+sku.toUpperCase()  + ".txt";
			ftputil.uploadFile("amazon_follow_html/" +authid+"/"+marketplaceid,sku.toUpperCase()  + ".txt",new DataInputStream(new ByteArrayInputStream(html.getBytes())));
		} catch (IOException e) {
			contextpath = null;
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return contextpath;
	}

	public static boolean mkDirectory(String path) {
		File file = null;
		try {
			file = new File(path);
			if (!file.exists()) {
				return file.mkdirs();
			} else {
				return false;
			}
		} catch (Exception e) {
		} finally {
			file = null;
		}
		return false;
	}

	public Integer getOfferNumber(Document doc) {
		Integer result = 0;
		NodeList nodelist = doc.getElementsByTagName("NumberOfOffers");
		Node node = nodelist.item(0);
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if(child.getNodeName().equals("OfferCount")){
				String name = child.getAttributes().getNamedItem("condition").getNodeValue();
				if(!"used".equals(name)) {
					result += Integer.parseInt(child.getTextContent());
				}
			}
		}
		return result;
	}

	public List<Map<String, Object>> getMap(Document doc, String parent, String childrenStr) {
		String a[] = childrenStr.split(",");
		Set<String> tagset = new HashSet<String>();
		List<Map<String, Object>> resultList = new ArrayList<Map<String,Object>>();
		for (int i = 0; i < a.length; i++) {
			tagset.add(a[i]);
		}
		NodeList nodelist = doc.getElementsByTagName(parent);
		for(int j = 0; j < nodelist.getLength(); j++) {
			Map<String, Object> result = new HashMap<String, Object>();
			Node node = nodelist.item(j);
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (tagset.contains(child.getNodeName())) {
					result.put(child.getNodeName(), child.getTextContent());
				}
			}
			resultList.add(j, result);
		}
		return resultList;
	}
	
	public List<Map<String, Object>> getMap(Document doc, String proparent, String parent, String childrenStr) {
		String a[] = childrenStr.split(",");
		Set<String> tagset = new HashSet<String>();
		List<Map<String, Object>> resultList = new ArrayList<Map<String,Object>>();
		for (int i = 0; i < a.length; i++) {
			tagset.add(a[i]);
		}
		NodeList nodelist = doc.getElementsByTagName(proparent);
		for(int j = 0; j < nodelist.getLength(); j++) {
			Map<String, Object> result = new HashMap<String, Object>();
			Node node = nodelist.item(j);
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (parent.equals(child.getNodeName())) {
					NodeList childrens = child.getChildNodes();
					for (int k = 0; k < childrens.getLength(); k++) {
						Node childs = childrens.item(k);
						if (tagset.contains(childs.getNodeName())) {
							result.put(childs.getNodeName(), childs.getTextContent());
						}
					}
				}
			}
			resultList.add(j, result);
		}
		return resultList;
	}
	
 
	public void sendProductFollowWxMsg(String sellerid)
	  {
	    if(sellerid==null) {
	    	return ;
	    }
	    AmazonAuthority auth = this.amazonAuthorityService.selectBySellerId(sellerid);
	    if(auth==null)return;
	
		Calendar c=Calendar.getInstance();
		c.add(Calendar.HOUR_OF_DAY, -2);
		QueryWrapper<FollowOfferChange> query=new QueryWrapper<FollowOfferChange>();
		query.eq("sellerid", sellerid);
		query.ge("findtime", c.getTime());
 
		
 
		List<FollowOfferChange> list = followOfferChangeService.list(query);
		Map<String,String> weixincontext=new HashMap<String,String>();
		String webcontext="";
		String shopid = "";
		for(FollowOfferChange item:list) {
			List<Map<String, Object>> ownerlist =null;// this.productInfoReviewMapper.selectOwnerByInfo(item.getMarketplaceid(), item.getAsin());
			 if ((ownerlist != null) && (ownerlist.size() > 0))
			    {
			      Map<String, Object> myitem = (Map)ownerlist.get(0);
			      String gname = myitem.get("gname").toString();
			      String market = myitem.get("market").toString();
			        shopid = myitem.get("shopid").toString();
			      String owner=myitem.get("owner")==null?null:myitem.get("owner").toString();
			      if ((shopid != null) && (auth != null) && 
			        (shopid.equals(auth.getShopId()))) {
			        return;
			      }
			      String key1="商品ASIN:"+item.getAsin()+"在店铺"+gname+"的"+market+"站点被SellerID为"+sellerid+"的商家跟卖，请及时处理。";
			      if(owner==null) {
			    	  webcontext=key1+"<br/>"+webcontext;
			    	  key1=key1+"\\\\n" +weixincontext.get(shopid);
			    	  weixincontext.put("shop",key1);
			     
			      }else {
			     	  webcontext=key1+"<br/>"+webcontext;
			    	  key1=key1+"\\\\n" +weixincontext.get(owner);
			    	  weixincontext.put(owner,key1);
			    	   }
		    }
	   
		
	    }
		String first="商品分析-发现跟卖提醒";
		
		String url="https://erp.wimoor.com/page.do?location=3a089e9e-ab14-11e6-bab5-00e04c023f0e";
//		notifyHandlerService.createRemind(NotifyTarget.productAnalysis, "like", Constants.sysUser, auth.getShopId(), webcontext, first);
//		for(Entry<String, String> context:weixincontext.entrySet()) {
//			if(context.getKey().equals("shop")) {
//				 userService.sendWechatMsg(first, context.getValue(), url, shopid, "FX",null);
//			 }else {
//				 userService.sendWechatMsg(first, context.getValue(), url, shopid, "FX",context.getKey());
//			 }
//		}
		
	  }
	@Override
	public boolean handlerMessage(JSONObject body) {
		// TODO Auto-generated method stub
		JSONObject anyOfferChangedNotification = body.getJSONObject("AnyOfferChangedNotification");
		String sellerId=anyOfferChangedNotification.getString("SellerId");
		JSONObject offerChangeTrigger=anyOfferChangedNotification.getJSONObject("OfferChangeTrigger");
		String marketplaceId=offerChangeTrigger.getString("MarketplaceId");
		String asin=offerChangeTrigger.getString("ASIN");
		String timeOfOfferChange=offerChangeTrigger.getString("TimeOfOfferChange");
		try {
			JSONObject summary = anyOfferChangedNotification.getJSONObject("Summary");
			if(summary==null) {
				return true;
			}
			JSONArray numberOfOffers = summary.getJSONArray("NumberOfOffers");
			int number=0;
			for(int i=0;i<numberOfOffers.size();i++) {
				number=number+numberOfOffers.getJSONObject(i).getIntValue("OfferCount");
				
			}
		    AmazonAuthority auth = this.amazonAuthorityService.selectBySellerId(sellerId);
		    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			sdf.setTimeZone(TimeZone.getDefault());
			recordProductFollow(auth.getId(), asin, marketplaceId, sdf.parse(timeOfOfferChange), number);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			JSONArray offers = anyOfferChangedNotification.getJSONArray("Offers");
			recordFollowOffer(offers, marketplaceId);
			recordFollowOfferChange(offers, asin, marketplaceId);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

}
