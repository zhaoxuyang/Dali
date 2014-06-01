package at.favre.lib.dali.builder.blur;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import at.favre.lib.dali.Dali;
import at.favre.lib.dali.R;
import at.favre.lib.dali.blur.EBlurAlgorithm;
import at.favre.lib.dali.blur.IBlur;
import at.favre.lib.dali.blur.algorithms.RenderScriptGaussianBlur;
import at.favre.lib.dali.builder.ABuilder;
import at.favre.lib.dali.builder.ContextWrapper;
import at.favre.lib.dali.builder.ImageReference;
import at.favre.lib.dali.builder.TwoLevelCache;
import at.favre.lib.dali.builder.exception.BlurWorkerException;
import at.favre.lib.dali.builder.processor.BrightnessProcessor;
import at.favre.lib.dali.builder.processor.ContrastProcessor;
import at.favre.lib.dali.builder.processor.ImageOverlayProcessor;
import at.favre.lib.dali.builder.processor.IBitmapProcessor;
import at.favre.lib.dali.util.BuilderUtil;

/**
 * Created by PatrickF on 26.05.2014.
 */
public class BlurBuilder extends ABuilder {
	private final static String TAG = BlurBuilder.class.getSimpleName();
	private final static int FADE_IN_MS = 200;

	private BlurData data;
	private Handler uiThreadHandler = new Handler(Looper.getMainLooper());

	public static class BlurData extends ABuilder.Data {
		public BitmapFactory.Options options = new BitmapFactory.Options();
		public boolean copyBitmapBeforeBlur = false;
		public boolean rescaleIfDownscaled = false;
		public boolean shouldCache = true;
		public ImageReference imageReference;
		public ContextWrapper contextWrapper;
		public List<IBitmapProcessor> preProcessors = new ArrayList<IBitmapProcessor>();
		public List<IBitmapProcessor> postProcessors = new ArrayList<IBitmapProcessor>();
		public TwoLevelCache diskCacheManager;
		public String tag = UUID.randomUUID().toString();
		public int errorResId = R.drawable.ic_error_pic;
		public boolean alphaFadeIn = true;
	}

	public BlurBuilder(ContextWrapper contextWrapper, ImageReference imageReference, TwoLevelCache diskCacheManager) {
		data = new BlurData();
		data.imageReference = imageReference;
		data.contextWrapper = contextWrapper;
		data.blurAlgorithm = new RenderScriptGaussianBlur(data.contextWrapper.getRenderScript());
		data.diskCacheManager = diskCacheManager;
	}


	/**
	 * @param blurRadius the views use to blur the view, default is {@link at.favre.lib.dali.builder.BuilderDefaults#BLUR_RADIUS};
	 * @throws java.lang.IllegalStateException if blurradius not in range [{@link at.favre.lib.dali.builder.BuilderDefaults#BLUR_RADIUS_MIN},{@link at.favre.lib.dali.builder.BuilderDefaults#BLUR_RADIUS_MAX}}
	 */
	public BlurBuilder blurRadius(int blurRadius) {
		BuilderUtil.checkBlurRadiusPrecondition(blurRadius);
		data.blurRadius = blurRadius;
		return this;
	}

	/**
	 * If this the image bitmap should be copied before blur.
	 *
	 * This will increase memory (RAM) usage while blurring, but
	 * if the bitmap's object is used anywhere else it would
	 * create side effects.
	 *
	 * @param shouldCopy
	 */
	public BlurBuilder copyBitmapBeforeProcess(boolean shouldCopy) {
		data.copyBitmapBeforeBlur = shouldCopy;
		return this;
	}

	/**
	 * Will scale the image down before processing for
	 * performance enhancement and less memory usage
	 * sacrificing image quality.
	 *
	 * @param scaleInSample value > 1 will scale the image width/height, so 2 will getFromDiskCache you 1/4
	 *                      of the original size and 4 will getFromDiskCache you 1/16 of the original size - this just sets
	 *                      the inSample size in {@link android.graphics.BitmapFactory.Options#inSampleSize } and
	 *                      behaves exactly the same, so keep the value 2^n for least scaling artifacts
	 */
	public BlurBuilder downScale(int scaleInSample) {
		data.options.inSampleSize = Math.min(Math.max(1, scaleInSample), 16384);
		return this;
	}

	/**
	 * Artificially rescales the image if downscaled before to
	 * it's original width/height
	 */
	public BlurBuilder reScale() {
		data.rescaleIfDownscaled = true;
		return this;
	}

	/**
	 * Set your custom decoder options here. Mind that that may
	 * overwrite the value in {@link #downScale(int)} ()};
	 *
	 * @param options non-null
	 */
	public BlurBuilder options(BitmapFactory.Options options) {
		if(options != null) {
			data.options = options;
		}
		return this;
	}

	/**
	 * Add custom processor. This will be applied to the
	 * image BEFORE blurring. The order in which this is
	 * calls defines the order the processors are applied.
	 *
	 * @param processor
	 */
	public BlurBuilder addPreProcessor(IBitmapProcessor processor) {
		data.preProcessors.add(processor);
		return this;
	}

	/**
	 * Set brightness to  eg. darken the resulting image for use as background
	 *
	 * @param brightness default is 0, pos values increase brightness, neg. values decrease brightness
	 *                   .-100 is black, positive goes up to 1000+
	 */
	public BlurBuilder brightness(float brightness) {
		data.postProcessors.add(new BrightnessProcessor(data.contextWrapper.getRenderScript(),brightness));
		return this;
	}

	/**
	 * Change contrast of the image
	 *
	 * @param contrast default is 0, pos values increase contrast, neg. values decrease contrast
	 */
	public BlurBuilder contrast(float contrast) {
		data.postProcessors.add(new ContrastProcessor(data.contextWrapper.getRenderScript(),Math.max(Math.min(1500.f,contrast),-1500.f)));
		return this;
	}

	public BlurBuilder frostedGlass() {
		data.postProcessors.add(new ImageOverlayProcessor(data.contextWrapper.getRenderScript(),data.contextWrapper.getContext().getResources()));
		return this;
	}

	/**
	 * Add custom processor. This will be applied to the
	 * image AFTER blurring. The order in which this is
	 * calls defines the order the processors are applied.
	 *
	 * @param processor
	 */
	public BlurBuilder addPostProcessor(IBitmapProcessor processor) {
		data.postProcessors.add(processor);
		return this;
	}

	/**
	 * Sets the blur algorithm.
	 *
	 * NOTE: this probably never is necessary to do except for testing purpose, the default
	 * algorithm, which uses Android's {@link android.support.v8.renderscript.ScriptIntrinsicBlur}
	 * which is the best and fastest you getFromDiskCache on Android suffices in nearly every situation
	 *
	 * @param algorithm
	 */
	public BlurBuilder algorithm(EBlurAlgorithm algorithm) {
		data.blurAlgorithm = BuilderUtil.getIBlurAlgorithm(algorithm, data.contextWrapper);

		return this;
	}

	/**
	 * Provide your custom blur implementation
	 * @param blurAlgorithm
	 */
	public BlurBuilder algorithm(IBlur blurAlgorithm) {
		data.blurAlgorithm = blurAlgorithm;
		return this;
	}

	/**
	 * Skips the cache (lookup & save). This will also delete all
	 * saved caches for this configuration. Use this if you only
	 * use this image once or want to purge the cache for this.
	 */
	public BlurBuilder skipCache() {
		data.shouldCache = false;
		return this;
	}

	/**
	 * Tags this builder's worker, so it could be later canceld by {@link at.favre.lib.dali.builder.ExecutorManager#cancelByTag(String)}
	 */
	public BlurBuilder tag(String tag) {
		data.tag = tag;
		return this;
	}

	/**
	 * Set the image that is set when an error occurs
	 * @param resId - e.g. R.drawable.error_image or {@link at.favre.lib.dali.Dali#NO_IMAGE_RESID} if you want to disable error image
	 */
	public BlurBuilder error(int resId) {
		data.errorResId = resId;
		return this;
	}

	public BlurBuilder noFade() {
		data.alphaFadeIn = false;
		return this;
	}

	/* GETTER METHODS ************************************************************************* */


	public JobDescription into(final ImageView imageView) {
		Dali.getExecutorManager().submitThreadPool(new BlurWorker(data, new BlurWorker.BlurWorkerListener() {
			@Override
			public void onResult(final BlurWorker.Result result) {
				//run on ui thread because we need to modify ui
				uiThreadHandler.post(new Runnable() {
					@Override
					public void run() {
						if (result.isError()) {
							Log.e(TAG, "Could not set into imageview", result.getThrowable());
							if(data.errorResId == Dali.NO_IMAGE_RESID) {
								imageView.setImageResource(data.errorResId);
							}
						} else {
							if(data.alphaFadeIn) {
								TransitionDrawable transition = new TransitionDrawable(new Drawable[] {
										new ColorDrawable(Color.parseColor("#00FFFFFF")),new BitmapDrawable(data.contextWrapper.getResources(), result.getBitmap())
								});
								imageView.setImageDrawable(transition);
								transition.startTransition(FADE_IN_MS);
							} else {
								imageView.setImageDrawable(new BitmapDrawable(data.contextWrapper.getResources(), result.getBitmap()));
							}
						}
					}
				});

			}
		}),data.tag);

		return getJobDescription();
	}

	public BitmapDrawable get() {
		return new BitmapDrawable(data.contextWrapper.getResources(),getAsBitmap());
	}

	public Bitmap getAsBitmap() {
		Future<BlurWorker.Result> result = Dali.getExecutorManager().submitThreadPool(new BlurWorker(data),data.tag);
		BlurWorker.Result r = null;
		try {
			r = result.get();
		} catch (Exception e) {
			throw new BlurWorkerException("Could not get bitmap from future",e);
		}

		if(r != null) {
			if(r.isError()) {
				throw new BlurWorkerException(r.getThrowable());
			} else {
				return r.getBitmap();
			}
		}
		throw new BlurWorkerException("result was null");
	}

	public JobDescription getJobDescription() {
		return new JobDescription(BuilderUtil.getCacheKey(data),BuilderUtil.getBuilderDescription(data), data.tag);
	}

	public static class JobDescription {
		public final String cacheKey;
		public final String builderDescription;
		public final String tag;

		public JobDescription(String cacheKey, String builderDescription, String tag) {
			this.cacheKey = cacheKey;
			this.builderDescription = builderDescription;
			this.tag = tag;
		}
	}

	public interface TaskFinishedListener {
		public void onBitmapReady(Bitmap manipulatedBitmap);
		public void onError(Throwable t);
	}

}
